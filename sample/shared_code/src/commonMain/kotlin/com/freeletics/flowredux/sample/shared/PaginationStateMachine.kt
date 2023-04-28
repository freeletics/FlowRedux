package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * It's callend `Internal` because it is note meant to be accessed publicly as it exposes coroutines
 * Flow and suspending function to dispatch.
 *
 * Instead the "wrappter class" [PaginationStateMachine] should be used which hides `Flow` etc.
 * but uses traditional "callbacks". That way it is easier to use on iOS.
 */
class InternalPaginationStateMachine(
    private val githubApi: GithubApi,
) : FlowReduxStateMachine<PaginationState, Action>(LoadFirstPagePaginationState) {
    init {
        spec {

            inState<LoadFirstPagePaginationState> {
                onEnter { loadFirstPage(it) }
            }

            inState<LoadingFirstPageError> {
                on<RetryLoadingFirstPage> { _, state ->
                    state.override { LoadFirstPagePaginationState }
                }
            }

            inState<ShowContentPaginationState> {
                on<LoadNextPage> { _, state ->
                    moveToLoadNextPageStateIfCanLoadNextPage(state)
                }
            }

            inState<ShowContentPaginationState>(additionalIsInState = {
                it.canLoadNextPage && it.nextPageLoadingState == NextPageLoadingState.LOADING
            }) {
                onEnter { loadNextPage(it) }
            }

            inState<ShowContentPaginationState>(additionalIsInState = {
                it.nextPageLoadingState == NextPageLoadingState.ERROR
            }) {
                onEnter { showPaginationErrorFor3SecsThenReset(it) }
            }

            inState<ShowContentPaginationState> {
                onActionStartStateMachine(
                    stateMachineFactory = { action: ToggleFavoriteAction, state: ShowContentPaginationState ->
                        val repo = state.items.find { it.id == action.id }!!
                        MarkAsFavoriteStateMachine(
                            githubApi = githubApi,
                            repository = repo,
                        )
                    },
                ) { inputState: State<ShowContentPaginationState>, childState: GithubRepository ->
                    inputState.mutate {
                        copy(
                            items = items.map { repoItem ->
                                if (repoItem.id == childState.id) {
                                    childState
                                } else {
                                    repoItem
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun moveToLoadNextPageStateIfCanLoadNextPage(
        state: State<ShowContentPaginationState>,
    ): ChangedState<PaginationState> {
        return if (!state.snapshot.canLoadNextPage) {
            state.noChange()
        } else {
            state.mutate {
                copy(
                    nextPageLoadingState = NextPageLoadingState.LOADING,
                )
            }
        }
    }

    /**
     * Loads the first page
     */
    private suspend fun loadFirstPage(
        state: State<LoadFirstPagePaginationState>,
    ): ChangedState<PaginationState> {
        val nextState = try {
            when (val pageResult: PageResult = githubApi.loadPage(page = 0)) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = emptyList(),
                        canLoadNextPage = false,
                        currentPage = 1,
                        nextPageLoadingState = NextPageLoadingState.IDLE,
                    )
                }
                is PageResult.Page -> {
                    ShowContentPaginationState(
                        items = pageResult.items,
                        canLoadNextPage = true,
                        currentPage = pageResult.page,
                        nextPageLoadingState = NextPageLoadingState.IDLE,
                    )
                }
            }
        } catch (t: Throwable) {
            LoadingFirstPageError(t)
        }

        return state.override { nextState }
    }

    private suspend fun loadNextPage(
        state: State<ShowContentPaginationState>,
    ): ChangedState<PaginationState> {
        val nextPageNumber = state.snapshot.currentPage + 1
        val nextState: ChangedState<ShowContentPaginationState> = try {
            when (val pageResult = githubApi.loadPage(page = nextPageNumber)) {
                PageResult.NoNextPage -> {
                    state.mutate {
                        copy(
                            nextPageLoadingState = NextPageLoadingState.IDLE,
                            canLoadNextPage = false,
                        )
                    }
                }
                is PageResult.Page -> {
                    state.mutate {
                        copy(
                            items = items + pageResult.items,
                            canLoadNextPage = true,
                            currentPage = nextPageNumber,
                            nextPageLoadingState = NextPageLoadingState.IDLE,
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            state.mutate {
                copy(
                    nextPageLoadingState = NextPageLoadingState.ERROR,
                )
            }
        }

        return nextState
    }

    private suspend fun showPaginationErrorFor3SecsThenReset(
        state: State<ShowContentPaginationState>,
    ): ChangedState<PaginationState> {
        delay(3000)
        return state.mutate {
            copy(
                nextPageLoadingState = NextPageLoadingState.IDLE,
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
    private val scope: CoroutineScope,
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
