package com.freeletics.flowredux2.sample.shared

import androidx.compose.runtime.Immutable

/**
 * A github repository
 */
@Immutable
data class GithubRepository(
    val id: String,
    val name: String,
    val stargazersCount: Int,
    val favoriteStatus: FavoriteStatus,
)
