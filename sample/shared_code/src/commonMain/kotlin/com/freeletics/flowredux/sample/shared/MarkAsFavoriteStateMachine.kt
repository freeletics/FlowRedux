package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.MutateState

data class MarkAsFavoriteState(val id: String, val status: FavoriteStatus)

class MarkAsFavoriteStateMachine(
    private val githubApi: GithubApi,
    initialState: MarkAsFavoriteState,
) : FlowReduxStateMachine<MarkAsFavoriteState, Action>( // Action Type == Nothing because we dont have any Action to deal here
    initialState = initialState
) {
    init {
        spec {
            inState<MarkAsFavoriteState>(additionalIsInState = { it.status == FavoriteStatus.MARKING_IN_PROGRESS }) {
                onEnter(::markAsFavorite)
            }
        }
    }

    private suspend fun markAsFavorite(stateSnapshot: MarkAsFavoriteState): ChangeState<MarkAsFavoriteState> {
        return try {
            githubApi.markAsFavorite(stateSnapshot.id)
            MutateState { copy(status = FavoriteStatus.FAVORITE) }
        } catch (e: Exception) {
            MutateState { copy(status = FavoriteStatus.FAILED_MARKING_AS_FAVORITE) }
        }
    }
}
