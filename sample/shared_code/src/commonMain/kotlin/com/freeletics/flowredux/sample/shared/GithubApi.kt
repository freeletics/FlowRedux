package com.freeletics.flowredux.sample.shared

import kotlinx.coroutines.delay

class GithubApi {
    private val pageSize = 30

    // Used to simulate network errors
    private var counter = 0
    private fun shouldFail(): Boolean =
            counter++ % 4 == 0

    suspend fun loadPage(page: Int): PageResult {
        delay(2000)
        if (shouldFail())
            throw Exception("Faked network error")
        val start = page * pageSize
        val end = min(githubData.size, page * pageSize + pageSize)

        return (
                if (start < githubData.size) githubData.subList(start, end) else emptyList<GithubRepository>()
                ).run {
                    // TODO rewrite this to use header Link
                    if (isEmpty()) {
                        PageResult.NoNextPage
                    } else {
                        PageResult.Page(page = page, items = this)
                    }
                }
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}

sealed class PageResult {
    internal object NoNextPage : PageResult()
    internal data class Page(val page: Int, val items: List<GithubRepository>) : PageResult()
}