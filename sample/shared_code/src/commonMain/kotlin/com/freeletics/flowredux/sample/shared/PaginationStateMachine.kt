package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.SetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    internal override  val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

/**
 * Indicates that the next page is loading
 * while still displaying the current items
 */
data class ShowContentAndLoadingNextPagePaginationState(
    override val items: List<GithubRepository>,
    internal override val currentPage: Int,
    internal override  val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

/**
 * Shows an Error while loading next page while still showing content
 */
data class ShowContentAndLoadingNextPageErrorPaginationState(
    override val items: List<GithubRepository>,
    internal override val currentPage: Int,
    internal override  val canLoadNextPage: Boolean
) : ContainsContentPaginationState()

/**
 * An error has occurred while loading the first page
 */
data class LoadingFirstPageError(val cause: Throwable) : PaginationState()

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
                on<RetryLoadingFirstPage> { _, _, setState ->
                    setState { LoadFirstPagePaginationState }
                }
            }

            inState<ShowContentPaginationState> {
                on<LoadNextPage>(block = ::moveToLoadNextPageStateIfCanLoadNextPage)
            }

            inState<ShowContentAndLoadingNextPagePaginationState> {
                onEnter(block = ::loadNextPage)
            }

            inState<ShowContentAndLoadingNextPageErrorPaginationState> {

            }
        }
    }

    private suspend fun moveToLoadNextPageStateIfCanLoadNextPage(
        action: LoadNextPage,
        getState: StateAccessor<PaginationState>,
        setState: SetState<PaginationState>
    ) {
        setState { state ->
            when (state) {
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
        getState: StateAccessor<PaginationState>,
        setState: SetState<PaginationState>
    ) {
        val nextState = try {
            val pageResult: PageResult = githubApi.loadPage(page = 1)
            when (pageResult) {
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

        setState { nextState }
    }

    /**
     * Either move to [ShowContentPaginationState] or
     * [ShowContentAndLoadingNextPageErrorPaginationState]
     */
    private suspend fun loadNextPage(
        getState: StateAccessor<PaginationState>,
        setState: SetState<PaginationState>
    ) {
        val state = getState()
        if (state !is ShowContentAndLoadingNextPagePaginationState)
            return // TODO should this throw an exception instead?

        val nextState = try {
            val pageResult: PageResult = githubApi.loadPage(page = state.currentPage)
            when (pageResult) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = state.items,
                        canLoadNextPage = false,
                        currentPage = state.currentPage
                    )
                }
                is PageResult.Page -> {
                    ShowContentPaginationState(
                        items = state.items + pageResult.items,
                        canLoadNextPage = true,
                        currentPage = state.currentPage
                    )
                }
            }
        } catch (t: Throwable) {
            ShowContentAndLoadingNextPageErrorPaginationState(
                items = state.items,
                canLoadNextPage = state.canLoadNextPage,
                currentPage = state.currentPage
            )
        }

        setState { nextState }
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

