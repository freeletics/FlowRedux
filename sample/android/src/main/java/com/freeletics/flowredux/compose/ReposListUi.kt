package com.freeletics.flowredux.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.FavoriteStatus
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.LoadNextPage
import com.freeletics.flowredux.sample.shared.RetryToggleFavoriteAction
import com.freeletics.flowredux.sample.shared.ToggleFavoriteAction

@Composable
fun ReposListUi(repos: List<GithubRepository>, loadMore: Boolean, dispatch: (Action) -> Unit) {
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = Modifier.wrapContentSize()) {
        itemsIndexed(repos) { index, repo ->
            GithubRepoUi(repo, dispatch)
            if (index == repos.size - 1) { // user scrolls until the end of the list.
                // identifying if user scrolled until the end can be done differently
                dispatch(LoadNextPage)
            }
        }

        if (loadMore) {
            item {
                LoadNextPageUi()
            }
        }
    }

    if (loadMore) {
        LaunchedEffect(loadMore) {
            listState.animateScrollToItem(repos.size)
        }
    }
}

@Composable
fun GithubRepoUi(repo: GithubRepository, dispatch: (Action) -> Unit) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            text = repo.stargazersCount.toString(),
        )
    }
}

@Composable
fun LoadNextPageUi() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
