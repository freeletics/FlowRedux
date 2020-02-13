package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
// TODO make [ObserveInStateSideEffectBuilder] work and remove this class.
internal class Working_ObserveInStateBuilder<T, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, S>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            when (flatMapPolicy) {
                FlatMapPolicy.LATEST ->
                    actions
                        .filterState(getState = getState, isInState = isInState)
                        .flatMapLatest { stateSubscription ->
                            when (stateSubscription) {
                                FilterState.StateChanged.SUBSCRIBE ->
                                    flow.flatMapLatest {
                                        setStateFlow(value = it, getState = getState)
                                    }
                                FilterState.StateChanged.UNSUBSCRIBE -> flow { }
                            }
                        }
                FlatMapPolicy.CONCAT -> actions
                    .filterState(getState = getState, isInState = isInState)
                    .flatMapLatest { stateSubscription ->
                        when (stateSubscription) {
                            FilterState.StateChanged.SUBSCRIBE ->
                                flow.flatMapConcat {
                                    setStateFlow(value = it, getState = getState)
                                }
                            FilterState.StateChanged.UNSUBSCRIBE -> flow { }
                        }
                    }
                FlatMapPolicy.MERGE -> actions
                    .filterState(getState = getState, isInState = isInState)
                    .flatMapLatest { stateSubscription ->
                        when (stateSubscription) {
                            FilterState.StateChanged.SUBSCRIBE ->
                                flow.flatMapMerge {
                                    setStateFlow(value = it, getState = getState)
                                }
                            FilterState.StateChanged.UNSUBSCRIBE -> flow { }
                        }
                    }
            }

        }
    }

    private suspend fun setStateFlow(
        value: T,
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {
        val setState = SetStateImpl<S>(
            defaultRunIf = { state -> isInState(state) },
            invokeCallback = { runOnlyIf, reduce ->
                emit(
                    SelfReducableAction<S, A>(
                        loggingInfo = "observeWhileInState<>", // TODO better logging
                        reduce = reduce,
                        runReduceOnlyIf = runOnlyIf
                    )
                )
            }
        )
        block(value, getState, setState)
    }

    /**
     * Internal implementation of an operator that keeps track if you have to subscribe
     * or unsubscribe of a certain Flow.
     *
     */
    private class FilterState<S : Any, A : Any>(
        actions: Flow<Action<S, A>>,
        getState: GetState<S>,
        private val isInState : (S) -> Boolean
    ) {

        private enum class InternalStateChangedSubscription {
            SUBSCRIBE, UNSUBSCRIBE, DO_NOTHING
        }

        internal enum class StateChanged {
            SUBSCRIBE, UNSUBSCRIBE
        }

        private val mutex = Mutex()
        private var lastState: S? = null

        internal val flow: Flow<StateChanged> = actions.map {
            mutex.withLock {

                val state = getState()
                val previousState = lastState
                val isInExpectedState = isInState(state)
                val previousStateInExpectedState = if (previousState == null) {
                    false
                } else {
                    isInState(previousState)
                }

                if (previousState == null) {
                    if (isInExpectedState) {
                        InternalStateChangedSubscription.SUBSCRIBE
                    } else {
                        InternalStateChangedSubscription.DO_NOTHING
                    }
                } else {
                    when {
                        isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.DO_NOTHING
                        isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.SUBSCRIBE
                        !isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.UNSUBSCRIBE
                        !isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.DO_NOTHING
                        else -> throw IllegalStateException(
                            "An internal error occurred: " +
                                "isInExpectedState: $isInExpectedState" +
                                "and previousStateInExpectedState: $previousStateInExpectedState " +
                                "is not possible. Please file an issue on Github."
                        )

                    }
                }.also {
                    lastState = state
                }

            }
        }
            .filter { it != InternalStateChangedSubscription.DO_NOTHING }
            .distinctUntilChanged()
            .map {
                when (it) {
                    InternalStateChangedSubscription.SUBSCRIBE -> StateChanged.SUBSCRIBE
                    InternalStateChangedSubscription.UNSUBSCRIBE -> StateChanged.UNSUBSCRIBE
                    InternalStateChangedSubscription.DO_NOTHING -> throw IllegalStateException(
                        "Internal Error occurred. File an issue on Github."
                    )
                }
            }
    }

    private fun Flow<Action<S, A>>.filterState(
        getState: GetState<S>,
        isInState : (S) -> Boolean
    ): Flow<FilterState.StateChanged> = FilterState<S, A>(
        actions = this,
        isInState = isInState,
        getState = getState
    ).flow
}
