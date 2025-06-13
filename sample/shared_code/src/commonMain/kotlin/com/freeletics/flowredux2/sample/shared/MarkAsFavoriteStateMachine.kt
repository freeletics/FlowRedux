package com.freeletics.flowredux2.sample.shared

import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachine
import com.freeletics.flowredux2.ChangeableState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class MarkAsFavoriteStateMachine(
    private val githubApi: GithubApi,
    repository: GithubRepository,
) : FlowReduxStateMachine<GithubRepository, Action>(
        initialState = repository.copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS),
    ) {
    private val favoriteStatusWhenStarting: FavoriteStatus = repository.favoriteStatus

    init {
        spec {
            inState<GithubRepository> {
                condition({ it.favoriteStatus == FavoriteStatus.OPERATION_IN_PROGRESS }) {
                    onEnter { markAsFavorite(it) }
                }

                condition({ it.favoriteStatus == FavoriteStatus.OPERATION_FAILED }) {
                    onEnter { resetErrorStateAfter3Seconds(it) }
                    on<RetryToggleFavoriteAction> { action, state -> resetErrorState(action, state) }
                }
            }
        }
    }

    private suspend fun markAsFavorite(state: ChangeableState<GithubRepository>): ChangedState<GithubRepository> {
        return try {
            val shouldBeMarkedAsFavorite = favoriteStatusWhenStarting == FavoriteStatus.NOT_FAVORITE
            githubApi.markAsFavorite(
                repoId = state.snapshot.id,
                favorite = shouldBeMarkedAsFavorite,
            )
            state.mutate {
                copy(
                    favoriteStatus = if (shouldBeMarkedAsFavorite) {
                        FavoriteStatus.FAVORITE
                    } else {
                        FavoriteStatus.NOT_FAVORITE
                    },
                    stargazersCount = if (shouldBeMarkedAsFavorite) {
                        stargazersCount + 1
                    } else {
                        stargazersCount - 1
                    },
                )
            }
        } catch (e: Exception) {
            state.mutate { copy(favoriteStatus = FavoriteStatus.OPERATION_FAILED) }
        }
    }

    private suspend fun resetErrorStateAfter3Seconds(state: ChangeableState<GithubRepository>): ChangedState<GithubRepository> {
        delay(3000)
        return state.mutate { copy(favoriteStatus = favoriteStatusWhenStarting) }
    }

    private fun resetErrorState(action: RetryToggleFavoriteAction, state: ChangeableState<GithubRepository>): ChangedState<GithubRepository> {
        return if (action.id != state.snapshot.id) {
            // Since all active MarkAsFavoriteStateMachine receive this action
            // we need to ignore those who are not meant for this state machine
            state.noChange()
        } else {
            state.mutate { copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS) }
        }
    }
}
