package com.freeletics.flowredux.sample.shared

/**
 * A github repository
 */
data class GithubRepository(
    val id: String,
    val name: String,
    val stargazersCount: Int,
    val favoriteStatus: FavoriteStatus
)
