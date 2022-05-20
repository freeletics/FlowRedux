package com.freeletics.flowredux.sample.shared

enum class FavoriteStatus {
    /**
     * It is not marked as favorite yet
     */
    NOT_FAVORITE,

    /**
     * An operation (read: http request) is in progress to either mark it as favorite or not mark it
     * as favorite
     */
    OPERATION_IN_PROGRESS,

    /**
     * The operation (read: http request) to either mark it as favorite or not mark it
     * as favorite has failed, so did not succeed.
     */
    OPERATION_FAILED,

    /**
     * Marked as favorites
     */
    FAVORITE
}
