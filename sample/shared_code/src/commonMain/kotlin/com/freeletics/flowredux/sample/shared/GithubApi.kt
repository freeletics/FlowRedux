package com.freeletics.flowredux.sample.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class GithubApi(
    private val baseUrl: String = "https://api.github.com/orgs/square/repos",
    private val httpClient: HttpClient
) {

    suspend fun loadPage(page: Int): PageResult =
        httpClient.get<List<GithubRepository>>(baseUrl).run {
            // TODO rewrite this to use header Link
            if (isEmpty()) {
                PageResult.NoNextPage
            } else {
                PageResult.Page(page = page, items = this)
            }
        }
}

sealed class PageResult {
    internal object NoNextPage : PageResult()
    internal data class Page(val page: Int, val items: List<GithubRepository>) : PageResult()
}