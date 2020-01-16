package com.freeletics.flowredux.sample.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An [Item] represents an entry that can be shown in the list
 */
sealed class Item

/**
 * Represents loading something
 */
object Loading : Item()

/**
 * A github repository
 */
@Serializable
data class GithubRepository(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("stargazers_count") val stargazersCount : Int
) : Item()