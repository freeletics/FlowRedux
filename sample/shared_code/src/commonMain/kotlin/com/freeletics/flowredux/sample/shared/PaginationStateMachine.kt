package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.dsl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Parent class for all actions
 */
sealed class Action

/**
 * Triggers reloading the first page. Should be only used while in [LoadingFirstPageError]
 */
object RetryLoadingFirstPage : Action()

/**
 * Triggers loading the next page. This is typically triggered if the user scrolls until the end
 * of the list and want to load the next page.
 */
object LoadNextPage : Action()

/**
 * parent class for all states
 */
sealed class PaginationState

/**
 * State that represents loading the first page
 */
object LoadFirstPagePaginationState : PaginationState()

/**
 * An error has occurred while loading the first page
 */
data class LoadingFirstPageError(val cause: Throwable) : PaginationState()

sealed class ContainsContentPaginationState : PaginationState() {
    abstract val items: List<GithubRepository>
    internal abstract val currentPage: Int
    internal abstract val canLoadNextPage: Boolean
}

/**
 * State that represents displaying a list of  [GithubRepository] items
 */
data class ShowContentPaginationState(
    override val items: List<GithubRepository>,
    internal override val currentPage: Int,
    internal override val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

/**
 * Indicates that the next page is loading
 * while still displaying the current items
 */
data class ShowContentAndLoadingNextPagePaginationState(
    override val items: List<GithubRepository>,
    internal override val currentPage: Int,
    internal override val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

/**
 * Shows an Error while loading next page while still showing content
 */
data class ShowContentAndLoadingNextPageErrorPaginationState(
    override val items: List<GithubRepository>,
    internal override val currentPage: Int,
    internal override val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

internal class InternalPaginationStateMachine(
    logger: FlowReduxLogger,
    private val githubApi: GithubApi
) : FlowReduxStateMachine<PaginationState, Action>(logger, LoadFirstPagePaginationState) {
    init {
        spec {

            inState<LoadFirstPagePaginationState> {
                onEnter(block = ::loadFirstPage)
            }

            inState<LoadingFirstPageError> {
                on<RetryLoadingFirstPage> { _, _ ->
                    SetState(LoadFirstPagePaginationState)
                }
            }

            inState<ShowContentPaginationState> {
                on<LoadNextPage>(block = ::moveToLoadNextPageStateIfCanLoadNextPage)
            }

            inState<ShowContentAndLoadingNextPagePaginationState> {
                onEnter(block = ::loadNextPage)
            }

            inState<ShowContentAndLoadingNextPageErrorPaginationState> {
                onEnter(block = ::moveToContentStateAfter3Seconds)
            }
        }
    }

    private suspend fun moveToLoadNextPageStateIfCanLoadNextPage(
        action: LoadNextPage,
        state: ShowContentPaginationState
    ): ChangeState<PaginationState> {
        return MutateState {
            when (val state = this) {
                is ShowContentPaginationState -> {
                    if (!state.canLoadNextPage)
                        state
                    else
                        ShowContentAndLoadingNextPagePaginationState(
                            items = state.items,
                            currentPage = state.currentPage + 1, // load next page
                            canLoadNextPage = true
                        )
                }
                else -> state
            }
        }
    }

    /**
     * Loads the first page
     */
    private suspend fun loadFirstPage(
        state: LoadFirstPagePaginationState
    ): ChangeState<PaginationState> {
        val nextState = try {
            when (val pageResult: PageResult = githubApi.loadPage(page = 0)) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = emptyList(),
                        canLoadNextPage = false,
                        currentPage = 1
                    )
                }
                is PageResult.Page -> {
                    ShowContentPaginationState(
                        items = pageResult.items,
                        canLoadNextPage = true,
                        currentPage = pageResult.page
                    )
                }
            }
        } catch (t: Throwable) {
            LoadingFirstPageError(t)
        }

        return SetState(nextState)
    }

    /**
     * Either move to [ShowContentPaginationState] or
     * [ShowContentAndLoadingNextPageErrorPaginationState]
     */
    private suspend fun loadNextPage(
        state: ShowContentAndLoadingNextPagePaginationState
    ): ChangeState<PaginationState> {
        val nextState = try {
            when (val pageResult = githubApi.loadPage(page = state.currentPage)) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = state.items,
                        canLoadNextPage = false,
                        currentPage = state.currentPage
                    )
                }
                is PageResult.Page -> { // TODO should be MutateState
                    ShowContentPaginationState(
                        items = state.items + pageResult.items,
                        canLoadNextPage = true,
                        currentPage = state.currentPage
                    )
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            ShowContentAndLoadingNextPageErrorPaginationState(
                items = state.items,
                canLoadNextPage = state.canLoadNextPage,
                currentPage = max(0, state.currentPage - 1)
            )
        }

        return SetState(nextState)
    }

    private suspend fun moveToContentStateAfter3Seconds(
        s: ShowContentAndLoadingNextPageErrorPaginationState
    ): ChangeState<PaginationState> {
        delay(3000)
        return MutateState {
            val state = this
            if (state is ShowContentAndLoadingNextPageErrorPaginationState)
                ShowContentPaginationState(
                    items = state.items,
                    currentPage = state.currentPage,
                    canLoadNextPage = state.canLoadNextPage
                )
            else state
        }
    }
}

class PaginationStateMachine(
    logger: FlowReduxLogger,
    githubApi: GithubApi,
    private val scope: CoroutineScope
) {
    private val stateMachine = InternalPaginationStateMachine(
        logger = logger,
        githubApi = githubApi
    )

    fun dispatch(action: Action) {
        scope.launch {
            stateMachine.dispatch(action)
        }
    }

    fun start(stateChangeListener: (PaginationState) -> Unit) {
        scope.launch {
            stateMachine.state.collect {
                stateChangeListener(it)
            }
        }
    }
}

