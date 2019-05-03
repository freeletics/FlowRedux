package com.freeletics.rxredux.businesslogic.pagination

import com.freeletics.flowredux.StateAccessor
import com.freeletics.rxredux.businesslogic.github.GithubApiFacade
import com.freeletics.rxredux.businesslogic.github.GithubRepository
import com.freeletics.rxredux.businesslogic.pagination.PaginationStateMachine.State
import com.freeletics.rxredux.reduxStore
import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.Relay
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Input Actions
 */
sealed class Action {
    /**
     * Load the next Page. This is typically invoked by the User by reaching the end of a list
     */
    object LoadNextPageAction : Action() {
        override fun toString(): String = LoadNextPageAction::class.java.simpleName
    }

    /**
     * Load the first Page. This is initially triggered when the app starts the first time
     * but not after screen orientation changes.
     */
    object LoadFirstPageAction : Action() {
        override fun toString(): String = LoadFirstPageAction::class.java.simpleName
    }
}

/**
 * An Error has occurred while loading next page
 * This is an internal Action and not public to the outside
 */
private data class ErrorLoadingPageAction(val error: Throwable, val page: Int) : Action() {
    override fun toString(): String =
        "${ErrorLoadingPageAction::class.java.simpleName} error=${error.message} page=$page"
}

/**
 * The page has been loaded. The result is attached to this Action.
 * This is an internal Action and not public to the outside
 */
private data class PageLoadedAction(
    val itemsLoaded: List<GithubRepository>,
    val page: Int
) : Action() {
    override fun toString(): String =
        "${PageLoadedAction::class.java.simpleName} itemsLoaded=${itemsLoaded.size} page=$page"
}

/**
 * Action that indicates that loading the next page has failed.
 * This only is used for loading next page but not first page.
 *
 * This is an internal Action and not public to the outside
 */
private data class ShowLoadNextPageErrorAction(val error: Throwable, val page: Int) : Action() {
    override fun toString(): String =
        "${ShowLoadNextPageErrorAction::class.java.simpleName} error=${error.message} page=$page"
}

/**
 * Hides the indicator that loading next page has failed.
 * This only is used for loading next page but not first page.
 *
 * This is an internal Action and not public to the outside
 */
private data class HideLoadNextPageErrorAction(val error: Throwable, val page: Int) : Action() {
    override fun toString(): String =
        "${HideLoadNextPageErrorAction::class.java.simpleName} error=${error.message} page=$page"
}


/**
 * This is an internal Action and not public to the outside
 */
private data class StartLoadingNextPage(val page: Int) : Action() {
    override fun toString(): String = "${StartLoadingNextPage::class.java.simpleName} page=$page"
}


/**
 * This statemachine handles loading the next pages.
 * It can have the States described in [State].
 */
class PaginationStateMachine @Inject constructor(
    private val github: GithubApiFacade
) {

    private interface ContainsItems {
        val items: List<GithubRepository>
        val page: Int
    }

    sealed class State {
        /**
         * Loading the first page
         */
        object LoadingFirstPageState : State() {
            override fun toString(): String = LoadingFirstPageState::class.java.simpleName
        }

        /**
         * An error while loading the first page has occurred
         */
        data class ErrorLoadingFirstPageState(val errorMessage: String) : State() {
            override fun toString(): String =
                "${ErrorLoadingFirstPageState::class.java.simpleName} error=$errorMessage"
        }

        /**
         * Show the content
         */
        data class ShowContentState(
            /**
             * The items that has been loaded so far
             */
            override val items: List<GithubRepository>,

            /**
             * The current Page
             */
            override val page: Int
        ) : State(), ContainsItems {
            override fun toString(): String =
                "${ShowContentState::class.java.simpleName} items=${items.size} page=$page"
        }

        /**
         * This also means loading the next page has been started.
         * The difference to [ShowContentState] is that this also means that a progress indicator at
         * the bottom of the list of items show be displayed
         */
        data class ShowContentAndLoadNextPageState(
            /**
             * The items that has been loaded so far
             */
            override val items: List<GithubRepository>,

            /**
             * The current Page
             */
            override val page: Int
        ) : State(), ContainsItems {
            override fun toString(): String =
                "${ShowContentAndLoadNextPageState::class.java.simpleName} items=${items.size} page=$page"
        }

        data class ShowContentAndLoadNextPageErrorState(
            /**
             * The items that has been loaded so far
             */
            override val items: List<GithubRepository>,

            /**
             * An error has occurred while loading the next page
             */
            val errorMessage: String,

            /**
             * The current Page
             */
            override val page: Int
        ) : State(), ContainsItems {
            override fun toString(): String =
                "${ShowContentAndLoadNextPageErrorState::class.java.simpleName} error=$errorMessage items=${items.size}"
        }
    }


    val input: Relay<Action> = PublishRelay.create()

    val state: Observable<State> = input
        .doOnNext { Timber.d("Input Action $it") }
        .reduxStore(
            initialState = State.LoadingFirstPageState,
            sideEffects = listOf(
                ::loadFirstPageSideEffect,
                ::loadNextPageSideEffect,
                ::showAndHideLoadingErrorSideEffect
            ),
            reducer = ::reducer
        )
        .distinctUntilChanged()
        .doOnNext { Timber.d("RxStore state $it") }

    /**
     * Loads the next Page
     */
    private fun nextPage(s: State): Observable<Action> {
        val nextPage = (if (s is ContainsItems) s.page else 0) + 1

        return github.loadNextPage(nextPage)
            .subscribeOn(Schedulers.io())
            .toObservable()
            .map<Action> { result ->
                PageLoadedAction(
                    itemsLoaded = result.items,
                    page = nextPage
                )
            }
            .delay(1, TimeUnit.SECONDS) // Add some delay to make the loading indicator appear,
            .onErrorReturn { error -> ErrorLoadingPageAction(error, nextPage) }
            .startWith(StartLoadingNextPage(nextPage))
    }

    /**
     * Load the first Page
     */
    private fun loadFirstPageSideEffect(
        actions: Observable<Action>,
        state: StateAccessor<State>
    ): Observable<Action> =
        actions.ofType(Action.LoadFirstPageAction::class.java)
            .filter { state() !is ContainsItems } // If first page has already been loaded, do nothing
            .switchMap {
                nextPage(state())
            }

    /**
     * A Side Effect that loads the next page
     */
    private fun loadNextPageSideEffect(
        actions: Observable<Action>,
        state: StateAccessor<State>
    ): Observable<Action> =
        actions
            .ofType(Action.LoadNextPageAction::class.java)
            .switchMap {
                nextPage(state())
            }

    /**
     * Shows and hides an error after a given time.
     * In UI a snackbar showing an error message would be shown / hidden respectively
     */
    private fun showAndHideLoadingErrorSideEffect(
        actions: Observable<Action>,
        state: StateAccessor<State>
    ): Observable<Action> =
        actions.ofType(ErrorLoadingPageAction::class.java)
            .filter { it.page > 1 }
            .switchMap { action ->
                Observable.timer(3, TimeUnit.SECONDS)
                    .map<Action> { HideLoadNextPageErrorAction(action.error, action.page) }
                    .startWith(ShowLoadNextPageErrorAction(action.error, action.page))
            }


    /**
     * The state reducer.
     * Takes Actions and the current state to calculate the new state.
     */
    private fun reducer(state: State, action: Action): State {
        Timber.d("Reducer reacts on $action. Current State $state")
        return when (action) {
            is StartLoadingNextPage -> {
                if (state is ContainsItems && state.page >= 1)
                // Load the next page (
                    State.ShowContentAndLoadNextPageState(items = state.items, page = state.page)
                else
                    State.LoadingFirstPageState
            }

            is PageLoadedAction -> {
                val items: List<GithubRepository> = if (state is ContainsItems) {
                    state.items + action.itemsLoaded
                } else action.itemsLoaded

                State.ShowContentState(
                    items = items,
                    page = action.page
                )
            }

            is ErrorLoadingPageAction -> if (action.page == 1) {
                State.ErrorLoadingFirstPageState(
                    action.error.localizedMessage
                )
            } else {
                state
            } // page > 1 is handled in showAndHideLoadingErrorSideEffect()

            is ShowLoadNextPageErrorAction -> {
                if (state !is ContainsItems) {
                    throw IllegalStateException("We never loaded the first page")
                }

                State.ShowContentAndLoadNextPageErrorState(
                    items = state.items,
                    page = state.page,
                    errorMessage = action.error.localizedMessage
                )
            }

            is HideLoadNextPageErrorAction -> {
                if (state !is ContainsItems) {
                    throw IllegalStateException("We never loaded the first page")
                }
                State.ShowContentState(
                    items = state.items,
                    page = state.page
                )
            }

            is Action.LoadFirstPageAction,
            is Action.LoadNextPageAction -> state // This is handled by loadNextPageSideEffect
        }
    }
}
