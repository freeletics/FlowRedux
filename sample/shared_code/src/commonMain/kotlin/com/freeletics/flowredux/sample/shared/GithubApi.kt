package com.freeletics.flowredux.sample.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get

class GithubApi(
    private val baseUrl: String = "https://api.github.com/orgs/square/repos",
    private val httpClient: HttpClient
) {

    suspend fun loadPage(): List<GithubRepository> =
        httpClient.get<List<GithubRepository>>(baseUrl)
}