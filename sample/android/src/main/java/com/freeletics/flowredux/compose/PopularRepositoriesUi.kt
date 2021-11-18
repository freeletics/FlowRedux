package com.freeletics.flowredux.compose

import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import com.freeletics.flowredux.AndroidFlowReduxLogger
import com.freeletics.flowredux.sample.shared.*

@Composable
fun PopularRepositoriesUi() {
    val stateMachine = InternalPaginationStateMachine(
        githubApi = GithubApi(),
        logger = AndroidFlowReduxLogger
    )

    val (state, dispatch) = stateMachine.stateAndDispatch()

    SampleTheme {
        Scaffold() {
            when (val s = state.value) {
                is LoadFirstPagePaginationState -> LoadingUi()
                is LoadingFirstPageError -> ErrorUi(dispatch)
                is ContainsContentPaginationState -> {
                    val loadNextPageUi: Boolean = s.shouldShowLoadMoreIndicator()
                    ReposListUi(repos = s.items, loadMore = loadNextPageUi, dispatch = dispatch)
                }
            }
        }
    }
}


private fun ContainsContentPaginationState.shouldShowLoadMoreIndicator(): Boolean = when (this) {
    is ShowContentPaginationState -> false
    is ShowContentAndLoadingNextPageErrorPaginationState -> false
    is ShowContentAndLoadingNextPagePaginationState -> true
}