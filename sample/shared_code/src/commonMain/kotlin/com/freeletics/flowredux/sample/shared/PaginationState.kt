package com.freeletics.flowredux.sample.shared

/**
 * parent class for all states
 */
sealed class PaginationState

/**
 * State that represents loading the first page
 */
object LoadFirstPagePaginationState : PaginationState()

/**
 * An error has occurred while loading the first page
 */
data class LoadingFirstPageError(val cause: Throwable) : PaginationState()

/**
 * Modeling state for Pull To Refresh and load next state
 */
enum class PageLoadingState {
    /**
     * Not doing pull to refresh
     */
    IDLE,

    /**
     * Loading is in Progress
     */
    LOADING,

    /**
     * An error has occurred while loading the next state
     */
    ERROR,
}

/**
 * State that represents displaying a list of  [GithubRepository] items
 */
data class ShowContentPaginationState(
    val items: List<GithubRepository>,
    val nextPageLoadingState: PageLoadingState,
    internal val currentPage: Int,
    internal val canLoadNextPage: Boolean,
) : PaginationState()
