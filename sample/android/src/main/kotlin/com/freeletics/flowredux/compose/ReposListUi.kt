package com.freeletics.flowredux.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.freeletics.flowredux.compose.components.LoadingUi
import com.freeletics.flowredux.sample.android.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.FavoriteStatus
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.LoadNextPage
import com.freeletics.flowredux.sample.shared.RetryToggleFavoriteAction
import com.freeletics.flowredux.sample.shared.ToggleFavoriteAction
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filter

internal object ReposListUiDefaults {
    const val VISIBLE_ITMES_THRESHOLD = 0
}

@Composable
internal fun ReposListUi(
    repos: ImmutableList<GithubRepository>,
    loadMore: Boolean,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    VISIBLE_ITMES_THRESHOLD: Int = ReposListUiDefaults.VISIBLE_ITMES_THRESHOLD,
) {
    LoadNextPageEffect(
        listState = listState,
        VISIBLE_ITMES_THRESHOLD = VISIBLE_ITMES_THRESHOLD,
        onLoadNextPage = { dispatch(LoadNextPage) },
    )

    if (loadMore) {
        LaunchedEffect(listState) {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            listState.animateScrollToItem(lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(
            items = repos,
            key = { it.id },
            contentType = { _ -> "GithubRepoUi" },
        ) { repo ->
            GithubRepoUi(
                modifier = Modifier
                    .fillParentMaxWidth(),
                repo = repo,
                dispatch = dispatch,
            )
        }

        if (loadMore) {
            item(
                key = "LoadNextPageUi",
                contentType = "LoadNextPageUi",
            ) {
                LoadNextPageUi(
                    modifier = Modifier
                        .fillParentMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoadNextPageEffect(
    listState: LazyListState,
    VISIBLE_ITMES_THRESHOLD: Int,
    onLoadNextPage: () -> Unit,
) {
    LaunchedEffect(listState, onLoadNextPage, VISIBLE_ITMES_THRESHOLD) {
        snapshotFlow { listState.layoutInfo }
            .filter { layoutInfo ->
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                val lastIndex = layoutInfo.totalItemsCount - 1

                lastVisibleIndex != null &&
                    lastIndex >= 0 &&
                    lastVisibleIndex + VISIBLE_ITMES_THRESHOLD >= lastIndex
            }
            .collect {
                // user scrolls until the end of the list.
                onLoadNextPage()
            }
    }
}

@Composable
private fun GithubRepoUi(
    repo: GithubRepository,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .wrapContentHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .weight(1f)
                    .fillMaxWidth(),
                text = repo.name,
            )

            when (repo.favoriteStatus) {
                FavoriteStatus.FAVORITE, FavoriteStatus.NOT_FAVORITE ->
                    Image(
                        modifier = Modifier
                            .wrapContentSize()
                            .clickable(enabled = true) { dispatch(ToggleFavoriteAction(repo.id)) },
                        painter = painterResource(
                            if (repo.favoriteStatus == FavoriteStatus.FAVORITE) {
                                R.drawable.ic_star_yellow_24dp
                            } else {
                                R.drawable.ic_star_black_24dp
                            },
                        ),
                        contentDescription = "Stars icon",
                    )

                FavoriteStatus.OPERATION_IN_PROGRESS -> LoadingUi(
                    Modifier
                        .width(24.dp)
                        .height(24.dp),
                )

                FavoriteStatus.OPERATION_FAILED -> Image(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp)
                        .wrapContentSize()
                        .clickable(enabled = true) { dispatch(RetryToggleFavoriteAction(repo.id)) },
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = "Stars icon error",
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                modifier = Modifier.width(50.dp),
                text = String.format(Locale.getDefault(), "%d", repo.stargazersCount),
            )
        }
    }
}

@Composable
private fun LoadNextPageUi(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
        )
    }
}
