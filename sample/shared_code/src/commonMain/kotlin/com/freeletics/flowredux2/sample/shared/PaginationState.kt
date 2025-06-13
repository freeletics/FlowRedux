package com.freeletics.flowredux2.sample.shared

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentList

/**
 * parent class for all states
 */
@Immutable
sealed interface PaginationState

/**
 * State that represents loading the first page
 */
data object LoadFirstPagePaginationState : PaginationState

/**
 * An error has occurred while loading the first page
 */
data class LoadingFirstPageError(val cause: Throwable) : PaginationState

/**
 * Modeling state for Pull To Refresh and load next state
 */
enum class NextPageLoadingState {
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
    val items: PersistentList<GithubRepository>,
    val nextPageLoadingState: NextPageLoadingState,
    internal val currentPage: Int,
    internal val canLoadNextPage: Boolean,
) : PaginationState
