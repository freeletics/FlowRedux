package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.dsl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

/**
 * It's callend `Internal` because it is note meant to be accessed publicly as it exposes coroutines
 * Flow and suspending function to dispatch.
 *
 * Instead the "wrappter class" [PaginationStateMachine] should be used which hides `Flow` etc.
 * but uses traditional "callbacks". That way it is easier to use on iOS.
 */
class InternalPaginationStateMachine(
    private val githubApi: GithubApi
) : FlowReduxStateMachine<PaginationState, Action>(LoadFirstPagePaginationState) {
    init {
        spec {

            inState<LoadFirstPagePaginationState> {
                onEnter(::loadFirstPage)
            }

            inState<LoadingFirstPageError> {
                on<RetryLoadingFirstPage> { _, _ ->
                    OverrideState(LoadFirstPagePaginationState)
                }
            }

            inState<ShowContentPaginationState> {
                on(::moveToLoadNextPageStateIfCanLoadNextPage)
            }

            inState<ShowContentAndLoadingNextPagePaginationState> {
                onEnter(::loadNextPage)
            }

            inState<ShowContentAndLoadingNextPageErrorPaginationState> {
                onEnter(::moveToContentStateAfter3Seconds)
            }
        }
    }

    private suspend fun moveToLoadNextPageStateIfCanLoadNextPage(
        action: LoadNextPage,
        stateSnapshot: ShowContentPaginationState
    ): ChangeState<PaginationState> {
        return if (!stateSnapshot.canLoadNextPage) {
            NoStateChange
        } else {
            OverrideState(
                ShowContentAndLoadingNextPagePaginationState(
                    items = stateSnapshot.items,
                    currentPage = stateSnapshot.currentPage + 1, // load next page
                    canLoadNextPage = true
                )
            )
        }
    }

    /**
     * Loads the first page
     */
    private suspend fun loadFirstPage(
        stateSnapshot: LoadFirstPagePaginationState
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

        return OverrideState(nextState)
    }

    /**
     * Either move to [ShowContentPaginationState] or
     * [ShowContentAndLoadingNextPageErrorPaginationState]
     */
    private suspend fun loadNextPage(
        stateSnapshot: ShowContentAndLoadingNextPagePaginationState
    ): ChangeState<PaginationState> {
        val nextState = try {
            when (val pageResult = githubApi.loadPage(page = stateSnapshot.currentPage)) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = stateSnapshot.items,
                        canLoadNextPage = false,
                        currentPage = stateSnapshot.currentPage
                    )
                }
                is PageResult.Page -> {
                    ShowContentPaginationState(
                        items = stateSnapshot.items + pageResult.items,
                        canLoadNextPage = true,
                        currentPage = stateSnapshot.currentPage
                    )
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            ShowContentAndLoadingNextPageErrorPaginationState(
                items = stateSnapshot.items,
                canLoadNextPage = stateSnapshot.canLoadNextPage,
                currentPage = max(0, stateSnapshot.currentPage - 1)
            )
        }

        return OverrideState(nextState)
    }

    private suspend fun moveToContentStateAfter3Seconds(
        stateSnapshot: ShowContentAndLoadingNextPageErrorPaginationState
    ): ChangeState<PaginationState> {
        delay(3000)
        return MutateState<ShowContentAndLoadingNextPageErrorPaginationState, PaginationState> {
            ShowContentPaginationState(
                items = items,
                currentPage = currentPage,
                canLoadNextPage = canLoadNextPage
            )
        }
    }
}

/**
 * A wrapper class around [InternalPaginationStateMachine] so that you dont need to deal with `Flow`
 * and suspend functions from iOS.
 */
class PaginationStateMachine(
    githubApi: GithubApi,
    private val scope: CoroutineScope
) {
    private val stateMachine = InternalPaginationStateMachine(githubApi = githubApi)

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

