package com.freeletics.flowredux2.sample.shared

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.initializeWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay

class MarkAsFavoriteStateMachine(
    private val githubApi: GithubApi,
    repository: GithubRepository,
) : FlowReduxStateMachineFactory<GithubRepository, Action>() {
    private val favoriteStatusWhenStarting: FavoriteStatus = repository.favoriteStatus

    init {
        initializeWith(reuseLastEmittedStateOnLaunch = false) {
            repository.copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        spec {
            inState<GithubRepository> {
                condition({ it.favoriteStatus == FavoriteStatus.OPERATION_IN_PROGRESS }) {
                    onEnter { markAsFavorite() }
                }

                condition({ it.favoriteStatus == FavoriteStatus.OPERATION_FAILED }) {
                    onEnter { resetErrorStateAfter3Seconds() }
                    on<RetryToggleFavoriteAction> { resetErrorState(it) }
                }
            }
        }
    }

    private suspend fun ChangeableState<GithubRepository>.markAsFavorite(): ChangedState<GithubRepository> {
        return try {
            val shouldBeMarkedAsFavorite = favoriteStatusWhenStarting == FavoriteStatus.NOT_FAVORITE
            githubApi.markAsFavorite(
                repoId = snapshot.id,
                favorite = shouldBeMarkedAsFavorite,
            )
            mutate {
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
        } catch (_: Exception) {
            mutate { copy(favoriteStatus = FavoriteStatus.OPERATION_FAILED) }
        }
    }

    private suspend fun ChangeableState<GithubRepository>.resetErrorStateAfter3Seconds(): ChangedState<GithubRepository> {
        delay(3000)
        return mutate { copy(favoriteStatus = favoriteStatusWhenStarting) }
    }

    private fun ChangeableState<GithubRepository>.resetErrorState(action: RetryToggleFavoriteAction): ChangedState<GithubRepository> {
        return if (action.id != snapshot.id) {
            // Since all active MarkAsFavoriteStateMachine receive this action
            // we need to ignore those who are not meant for this state machine
            noChange()
        } else {
            mutate { copy(favoriteStatus = FavoriteStatus.OPERATION_IN_PROGRESS) }
        }
    }
}
