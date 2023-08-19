package com.freeletics.flowredux.sample.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GithubApi {
    private val githubData = List(120) {
        GithubRepository(
            id = "$it",
            name = "Repository $it",
            stargazersCount = it * 10,
            favoriteStatus = FavoriteStatus.NOT_FAVORITE,
        )
    }

    private val pageSize = 30

    // Used to simulate network errors
    private var counter = 0
    private val counterMutex = Mutex()
    private suspend inline fun shouldFail(): Boolean =
        counterMutex.withLock { counter++ } % 4 == 0

    suspend fun loadPage(page: Int): PageResult {
        delay(2000)
        if (shouldFail()) {
            throw Exception("Faked network error")
        }
        val start = page * pageSize
        val end = min(githubData.size, page * pageSize + pageSize)

        return (
            if (start < githubData.size) {
                githubData.subList(
                    start,
                    end,
                )
            } else {
                emptyList()
            }
            ).run {
            if (isEmpty()) {
                PageResult.NoNextPage
            } else {
                PageResult.Page(page = page, items = this)
            }
        }
    }

    @Suppress("unused_parameter")
    suspend fun markAsFavorite(repoId: String, favorite: Boolean) {
        delay(2000) // simulate network effect
        if (shouldFail()) {
            throw Exception("Faked network error")
        }
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}

sealed class PageResult {
    internal data object NoNextPage : PageResult()
    internal data class Page(val page: Int, val items: List<GithubRepository>) : PageResult()
}

fun List<GithubRepository>.markAsFavorite(repoId: String, favorite: Boolean): List<GithubRepository> {
    return map {
        if (it.id == repoId) {
            it.copy(
                favoriteStatus = if (favorite) {
                    FavoriteStatus.FAVORITE
                } else {
                    FavoriteStatus.NOT_FAVORITE
                },
            )
        } else {
            it
        }
    }
}
