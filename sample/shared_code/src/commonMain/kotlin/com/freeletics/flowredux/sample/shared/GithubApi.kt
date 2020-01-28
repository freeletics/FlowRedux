package com.freeletics.flowredux.sample.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.io.IOException

class GithubApi(
    private val baseUrl: String = "https://api.github.com/organizations/82592/repos",
    private val httpClient: HttpClient
) {
    // Used to simulate network errors
    private var counter = 0
    private fun shouldFail() : Boolean =
        counter++ % 4 == 0

    suspend fun loadPage(page: Int): PageResult {
        if (shouldFail())
            throw IOException("Faked network error")

        return httpClient.get<List<GithubRepository>>("$baseUrl?page=$page").run {
            // TODO rewrite this to use header Link
            if (isEmpty()) {
                PageResult.NoNextPage
            } else {
                PageResult.Page(page = page, items = this)
            }
        }
    }
}

sealed class PageResult {
    internal object NoNextPage : PageResult()
    internal data class Page(val page: Int, val items: List<GithubRepository>) : PageResult()
}