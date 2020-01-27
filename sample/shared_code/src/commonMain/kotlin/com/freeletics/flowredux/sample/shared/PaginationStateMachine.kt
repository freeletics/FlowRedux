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
 * parent class for all states
 */
sealed class PaginationState

/**
 * State that represents loading the first page
 */
object LoadFirstPagePaginationState : PaginationState()

/**
 * State that represents displaying a list of  [GithubRepository] items
 */
data class ShowContentPaginationState(
    val items: List<GithubRepository>,
    private val currentPage: Int,
    private val canLoadNextPage: Boolean
) : PaginationState()

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

            }
        }
    }

    /**
     * Loads the first page
     */
    suspend fun loadFirstPage(
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
}

class PaginationStateMachine(
    logger: FlowReduxLogger,
    githubApi: GithubApi,
    private val scope: CoroutineScope,
    private val stateChangeListener: (PaginationState) -> Unit
) {
    private val stateMachine = InternalPaginationStateMachine(
        logger = logger,
        githubApi = githubApi
    )

    init {
        scope.launch {
            stateMachine.state.collect {
                stateChangeListener(it)
            }
        }
    }

    fun dispatch(action: Action) {
        scope.launch {
            stateMachine.dispatch(action)
        }
    }
}

