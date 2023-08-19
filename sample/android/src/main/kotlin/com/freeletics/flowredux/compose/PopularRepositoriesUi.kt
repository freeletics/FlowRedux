package com.freeletics.flowredux.compose

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freeletics.flowredux.compose.components.ErrorUi
import com.freeletics.flowredux.compose.components.LoadingUi
import com.freeletics.flowredux.sample.android.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.LoadFirstPagePaginationState
import com.freeletics.flowredux.sample.shared.LoadNextPage
import com.freeletics.flowredux.sample.shared.LoadingFirstPageError
import com.freeletics.flowredux.sample.shared.NextPageLoadingState
import com.freeletics.flowredux.sample.shared.PaginationState
import com.freeletics.flowredux.sample.shared.ShowContentPaginationState
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter

internal object PopularRepositoriesUiDefaults {
    const val VisibleItemsThreshold = 2
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PopularRepositoriesUi(
    state: PaginationState?,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    visibleItemsThreshold: Int = PopularRepositoriesUiDefaults.VisibleItemsThreshold,
) {
    SampleTheme {
        Scaffold(
            modifier = modifier,
            scaffoldState = scaffoldState,
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
                    val listState = rememberLazyListState()

                    val showLoadNextPageUi = state.shouldShowLoadMoreIndicator()
                    val showErrorSnackBar = state.shouldShowErrorSnackbar()

                    ReposListUi(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .consumeWindowInsets(paddingValues),
                        repos = state.items,
                        listState = listState,
                        loadMore = showLoadNextPageUi,
                        dispatch = dispatch,
                    )

                    // Effect to show error snackbar
                    if (showErrorSnackBar) {
                        val errorMessage = stringResource(R.string.unexpected_error)

                        LaunchedEffect(scaffoldState.snackbarHostState) {
                            scaffoldState.snackbarHostState.showSnackbar(
                                message = errorMessage,
                                duration = SnackbarDuration.Indefinite, // Will be dismissed by changing state
                            )
                        }
                    }

                    // Effect to scroll to the end of the list when loading next page
                    if (showLoadNextPageUi) {
                        val size = state.items.size

                        LaunchedEffect(listState, size) {
                            listState.animateScrollToItem(size)
                        }
                    }

                    // Effect to load next page
                    LaunchedEffect(listState, dispatch, visibleItemsThreshold) {
                        snapshotFlow { listState.layoutInfo }
                            .filter { layoutInfo ->
                                val index = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                val totalItemsCount = layoutInfo.totalItemsCount

                                index != null && index + visibleItemsThreshold >= totalItemsCount
                            }
                            .conflate()
                            .collect {
                                // user scrolls until the end of the list.
                                dispatch(LoadNextPage)
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
