package com.freeletics.flowredux.sample.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * A github repository
 */
@Serializable
data class GithubRepository(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("stargazers_count") val stargazersCount : Int
)