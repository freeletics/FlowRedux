package com.freeletics.rxredux.businesslogic.github

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubSearchResults(
    val items : List<GithubRepository>
)
