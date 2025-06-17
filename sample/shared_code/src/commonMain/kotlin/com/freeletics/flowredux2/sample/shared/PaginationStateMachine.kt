package com.freeletics.flowredux2.sample.shared

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * It's called `Internal` because it is not meant to be accessed publicly as it exposes coroutines
 * Flow and suspending function to dispatch.
 *
 * Instead the "wrapper class" [PaginationStateMachine] should be used which hides `Flow` etc.
 * but uses traditional "callbacks". That way it is easier to use on iOS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InternalPaginationStateMachine(
    private val githubApi: GithubApi,
) : FlowReduxStateMachine<PaginationState, Action>(LoadFirstPagePaginationState) {
    init {
        spec {
            inState<LoadFirstPagePaginationState> {
                onEnter { loadFirstPage() }
            }

            inState<LoadingFirstPageError> {
                on<RetryLoadingFirstPage> {
                    override { LoadFirstPagePaginationState }
                }
            }

            inState<ShowContentPaginationState> {
                on<LoadNextPage> {
                    moveToLoadNextPageStateIfCanLoadNextPage()
                }

                condition({ it.canLoadNextPage && it.nextPageLoadingState == NextPageLoadingState.LOADING }) {
                    onEnter { loadNextPage() }
                }

                condition({ it.nextPageLoadingState == NextPageLoadingState.ERROR }) {
                    onEnter { showPaginationErrorFor3SecsThenReset() }
                }

                onActionStartStateMachine(
                    stateMachineFactory = { action: ToggleFavoriteAction, state: ShowContentPaginationState ->
                        val repo = state.items.find { it.id == action.id }!!
                        MarkAsFavoriteStateMachine(
                            githubApi = githubApi,
                            repository = repo,
                        )
                    },
                ) { childState: GithubRepository ->
                    mutate {
                        copy(
                            items = items
                                .map { repoItem ->
                                    if (repoItem.id == childState.id) {
                                        childState
                                    } else {
                                        repoItem
                                    }
                                }
                                .toPersistentList(),
                        )
                    }
                }
            }
        }
    }

    private fun ChangeableState<ShowContentPaginationState>.moveToLoadNextPageStateIfCanLoadNextPage(): ChangedState<PaginationState> {
        return if (!snapshot.canLoadNextPage) {
            noChange()
        } else {
            mutate {
                copy(nextPageLoadingState = NextPageLoadingState.LOADING)
            }
        }
    }

    /**
     * Loads the first page
     */
    private suspend fun ChangeableState<LoadFirstPagePaginationState>.loadFirstPage(): ChangedState<PaginationState> {
        val nextState = try {
            when (val pageResult: PageResult = githubApi.loadPage(page = 0)) {
                PageResult.NoNextPage -> {
                    ShowContentPaginationState(
                        items = persistentListOf(),
                        canLoadNextPage = false,
                        currentPage = 1,
                        nextPageLoadingState = NextPageLoadingState.IDLE,
                    )
                }
                is PageResult.Page -> {
                    ShowContentPaginationState(
                        items = pageResult.items.toPersistentList(),
                        canLoadNextPage = true,
                        currentPage = pageResult.page,
                        nextPageLoadingState = NextPageLoadingState.IDLE,
                    )
                }
            }
        } catch (t: Throwable) {
            LoadingFirstPageError(t)
        }

        return override { nextState }
    }

    private suspend fun ChangeableState<ShowContentPaginationState>.loadNextPage(): ChangedState<PaginationState> {
        val nextPageNumber = snapshot.currentPage + 1
        val nextState: ChangedState<ShowContentPaginationState> = try {
            when (val pageResult = githubApi.loadPage(page = nextPageNumber)) {
                PageResult.NoNextPage -> {
                    mutate {
                        copy(
                            nextPageLoadingState = NextPageLoadingState.IDLE,
                            canLoadNextPage = false,
                        )
                    }
                }
                is PageResult.Page -> {
                    mutate {
                        copy(
                            items = items.addAll(pageResult.items),
                            canLoadNextPage = true,
                            currentPage = nextPageNumber,
                            nextPageLoadingState = NextPageLoadingState.IDLE,
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            mutate {
                copy(
                    nextPageLoadingState = NextPageLoadingState.ERROR,
                )
            }
        }

        return nextState
    }

    private suspend fun ChangeableState<ShowContentPaginationState>.showPaginationErrorFor3SecsThenReset(): ChangedState<PaginationState> {
        delay(3000)
        return mutate {
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
