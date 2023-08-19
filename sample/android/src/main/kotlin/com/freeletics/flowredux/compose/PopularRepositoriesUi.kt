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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

internal object PopularRepositoriesUiDefaults {
    const val VisibleItemsThreshold = 10
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

                    val curSize by rememberUpdatedState(newValue = state.items.size)

                    // Effect to scroll to the end of the list when loading next page
                    if (showLoadNextPageUi) {
                        val index = state.items.size + 1
                        Timber.tag("PopularRepositoriesUi").d("Scroll to $index, curSize=$curSize")

                        DisposableEffect(listState, scope) {
                            scope.launch {
                                listState.animateScrollToItem(curSize)
                            }
                            onDispose {  }
                        }
//
//                        LaunchedEffect(listState) {
//                            Timber.tag("PopularRepositoriesUi").d("Scroll to $size, curSize=$curSize")
//                            listState.animateScrollToItem(size)
//                            Timber.tag("PopularRepositoriesUi").d("Scroll to $size done, curSize=$curSize")
//                        }
                    }

                    // Effect to load next page
                    LaunchedEffect(listState, dispatch, visibleItemsThreshold) {
                        snapshotFlow { listState.layoutInfo }
                            .filter { layoutInfo ->
                                val index = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                val totalItemsCount = layoutInfo.totalItemsCount

                                Timber
                                    .tag("PopularRepositoriesUi")
                                    .d("layoutInfo: index=$index, totalItemsCount=$totalItemsCount")

                                index != null && index + visibleItemsThreshold >= totalItemsCount
                            }
                            .conflate()
                            .collect {
                                Timber
                                    .tag("PopularRepositoriesUi")
                                    .d("load next page")

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
