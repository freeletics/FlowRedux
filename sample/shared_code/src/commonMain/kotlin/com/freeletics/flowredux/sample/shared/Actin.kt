package com.freeletics.flowredux.sample.shared

/**
 * Parent class for all actions
 */
sealed class Action

/**
 * Triggers reloading the first page. Should be only used while in [LoadingFirstPageError]
 */
object RetryLoadingFirstPage : Action()

/**
 * Triggers loading the next page. This is typically triggered if the user scrolls until the end
 * of the list and want to load the next page.
 */
object LoadNextPage : Action()

/**
 * Mark a repository as favorite
 */
data class MarkRepositoryAsFavoriteAction(val id: String) : Action()
