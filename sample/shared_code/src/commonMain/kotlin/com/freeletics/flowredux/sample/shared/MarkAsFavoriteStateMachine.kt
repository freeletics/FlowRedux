package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.MutateState
import com.freeletics.flowredux.dsl.NoStateChange
import kotlinx.coroutines.delay

class MarkAsFavoriteStateMachine(
    private val githubApi: GithubApi,
    repository: GithubRepository,
) : FlowReduxStateMachine<GithubRepository, Action>(
    initialState = repository.copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS)
) {
    private val favoriteStatusWhenStarting: FavoriteStatus = repository.favoriteStatus

    init {
        spec {
            inState<GithubRepository>(additionalIsInState = { it.favoriteStatus == FavoriteStatus.OPERATION_IN_PROGRESS }) {
                onEnter(::markAsFavorite)
            }

            inState<GithubRepository>(additionalIsInState = { it.favoriteStatus == FavoriteStatus.OPERATION_FAILED }) {
                onEnter(::resetErrorStateAfter3Seconds)
                on(::resetErrorState)
            }
        }
    }

    private suspend fun markAsFavorite(stateSnapshot: GithubRepository): ChangeState<GithubRepository> {
        return try {
            val shouldBeMarkedAsFavorite = favoriteStatusWhenStarting == FavoriteStatus.NOT_FAVORITE
            githubApi.markAsFavorite(repoId = stateSnapshot.id, favorite = shouldBeMarkedAsFavorite)
            MutateState {
                copy(
                    favoriteStatus = if (shouldBeMarkedAsFavorite) FavoriteStatus.FAVORITE
                    else FavoriteStatus.NOT_FAVORITE,
                    stargazersCount = if (shouldBeMarkedAsFavorite) stargazersCount + 1
                    else stargazersCount - 1
                )
            }
        } catch (e: Exception) {
            MutateState { copy(favoriteStatus = FavoriteStatus.OPERATION_FAILED) }
        }
    }

    private suspend fun resetErrorStateAfter3Seconds(stateSnapshot: GithubRepository): ChangeState<GithubRepository> {
        delay(3000)
        return MutateState { copy(favoriteStatus = favoriteStatusWhenStarting) }
    }

    private suspend fun resetErrorState(action: RetryToggleFavoriteAction, stateSnapshot: GithubRepository): ChangeState<GithubRepository> {
        return if (action.id != stateSnapshot.id) {
            // Since all active MarkAsFavoriteStateMachine receive this action
            // we need to ignore those who are not meant for this state machine
            NoStateChange
        } else {
            MutateState { copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS) }
        }
    }
}
