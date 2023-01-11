package com.freeletics.flowredux.compose

import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.*
import kotlinx.coroutines.launch

@Composable
fun PopularRepositoriesUi(state: PaginationState?, dispatch: (Action) -> Unit) {
    val scaffoldState = rememberScaffoldState()
    SampleTheme {
        Scaffold(scaffoldState = scaffoldState) {
            when (state) {
                null, // null means state machine did not emit yet the first state --> in mean time show Loading
                is LoadFirstPagePaginationState,
                -> LoadingUi()
                is LoadingFirstPageError -> ErrorUi(dispatch)
                is ShowContentPaginationState -> {
                    val showLoadNextPageUi = state.shouldShowLoadMoreIndicator()
                    val showErrorSnackBar = state.shouldShowErrorSnackbar()

                    ReposListUi(repos = state.items, loadMore = showLoadNextPageUi, dispatch = dispatch)

                    val errorMessage = stringResource(R.string.unexpected_error)
                    if (showErrorSnackBar) {
                        LaunchedEffect(scaffoldState.snackbarHostState) {
                            launch {
                                scaffoldState.snackbarHostState.showSnackbar(
                                    errorMessage,
                                    duration = SnackbarDuration.Indefinite // Will be dismissed by changing state
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ShowContentPaginationState.shouldShowLoadMoreIndicator(): Boolean = when (this.nextPageLoadingState) {
    NextPageLoadingState.LOADING -> true
    else -> false
}

private fun ShowContentPaginationState.shouldShowErrorSnackbar(): Boolean = when (this.nextPageLoadingState) {
    NextPageLoadingState.ERROR -> true
    else -> false
}
