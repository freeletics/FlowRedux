package com.freeletics.flowredux.sample.shared

/**
 * Parent class for all actions
 */
sealed interface Action

/**
 * Triggers reloading the first page. Should be only used while in [LoadingFirstPageError]
 */
data object RetryLoadingFirstPage : Action

/**
 * Triggers loading the next page. This is typically triggered if the user scrolls until the end
 * of the list and want to load the next page.
 */
data object LoadNextPage : Action

/**
 * Mark a repository as favorite
 */
data class ToggleFavoriteAction(val id: String) : Action

/**
 * If an error has occurred while Toggling Favorite status, then you can retry with this action
 */
data class RetryToggleFavoriteAction(val id: String) : Action
