package com.freeletics.flowredux.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.LoadNextPage

sealed interface Item
data class RepoItem(val repo: GithubRepository) : Item
object LoadNextPageIndicatorItem : Item

@Composable
fun ReposListUi(repos: List<GithubRepository>, loadMore: Boolean, dispatch: (Action) -> Unit) {
    LazyColumn(modifier = Modifier.wrapContentSize()) {
        itemsIndexed(repos) { index, repo ->
            GithubRepoUi(repo)
            if (index == repos.size - 1) {
                dispatch(LoadNextPage)
            }
        }

        if (loadMore) {
            item {
                LoadNextPageUi()
            }
        }
    }
}

@Composable
fun GithubRepoUi(repo: GithubRepository) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {

        Text(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .fillMaxWidth(), text = repo.name
        )
        Image(
            modifier = Modifier.wrapContentSize(),
            painter = painterResource(R.drawable.ic_star_black_24dp),
            contentDescription = "Stars icon"
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.width(50.dp),
            text = repo.stargazersCount.toString()
        )

    }
}

@Composable
fun LoadNextPageUi() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center)
        )
    }
}