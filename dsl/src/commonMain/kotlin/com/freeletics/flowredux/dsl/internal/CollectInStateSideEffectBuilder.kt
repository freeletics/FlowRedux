package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.FlatMapPolicy
import com.freeletics.flowredux.dsl.InStateObserverBlock
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
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
internal class CollectInStateBuilder<T, InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    @ExperimentalCoroutinesApi
    @FlowPreview
    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .filterState(getState = getState, isInState = isInState)
                .flatMapLatest { stateSubscription ->
                    when (stateSubscription) {
                        FilterState.StateChanged.SUBSCRIBE -> {
                            flow.flatMapWithPolicy(flatMapPolicy) {
                                setStateFlow(value = it, getState = getState)
                            }
                        }
                        FilterState.StateChanged.UNSUBSCRIBE -> flow { }
                    }
                }
        }
    }

    private suspend fun setStateFlow(
        value: T,
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {

        runOnlyIfInInputState(getState, isInState) { inputState ->
            val changeState = block(value, inputState)
            emit(
                ChangeStateAction<S, A>(
                    loggingInfo = "collectWhileInState<>", // TODO better logging
                    changeState = changeState,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
    }

    /**
     * Internal implementation of an operator that keeps track if you have to subscribe
     * or unsubscribe of a certain Flow.
     *
     */
    private class FilterState<S : Any, A : Any>(
        actions: Flow<Action<S, A>>,
        getState: GetState<S>,
        private val isInState: (S) -> Boolean
    ) {

        private enum class InternalStateChangedSubscription {
            SUBSCRIBE, UNSUBSCRIBE, DO_NOTHING
        }

        enum class StateChanged {
            SUBSCRIBE, UNSUBSCRIBE
        }

        private val mutex = Mutex()
        private var lastState: S? = null

        val flow: Flow<StateChanged> = actions.map {
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
        isInState: (S) -> Boolean
    ): Flow<FilterState.StateChanged> = FilterState<S, A>(
        actions = this,
        isInState = isInState,
        getState = getState
    ).flow
}
