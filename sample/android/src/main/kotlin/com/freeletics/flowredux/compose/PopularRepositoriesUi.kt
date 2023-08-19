package com.freeletics.flowredux.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freeletics.flowredux.compose.components.ErrorUi
import com.freeletics.flowredux.compose.components.LoadingUi
import com.freeletics.flowredux.sample.android.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.LoadFirstPagePaginationState
import com.freeletics.flowredux.sample.shared.LoadingFirstPageError
import com.freeletics.flowredux.sample.shared.NextPageLoadingState
import com.freeletics.flowredux.sample.shared.PaginationState
import com.freeletics.flowredux.sample.shared.ShowContentPaginationState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PopularRepositoriesUi(
    state: PaginationState?,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    SampleTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->

            when (state) {
                null, // null means state machine did not emit yet the first state --> in mean time show Loading
                is LoadFirstPagePaginationState,
                -> LoadingUi(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues),
                )

                is LoadingFirstPageError -> ErrorUi(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues),
                    dispatch = dispatch,
                )

                is ShowContentPaginationState -> {
                    val showLoadNextPageUi = state.shouldShowLoadMoreIndicator()
                    val showErrorSnackBar = state.shouldShowErrorSnackbar()

                    ReposListUi(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .consumeWindowInsets(paddingValues),
                        repos = state.items,
                        loadMore = showLoadNextPageUi,
                        dispatch = dispatch,
                    )

                    if (showErrorSnackBar) {
                        val errorMessage = stringResource(R.string.unexpected_error)

                        DisposableEffect(snackbarHostState, scope) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = errorMessage,
                                    duration = SnackbarDuration.Short,
                                )
                            }

                            onDispose { }
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
