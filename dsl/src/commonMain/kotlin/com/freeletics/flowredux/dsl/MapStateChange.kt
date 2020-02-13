package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

/**
 * Internal implementation of an operator that keeps track if you a certain state has been
 * entered or left.
 */
internal class MapStateChange<S : Any, A : Any>(
    actions: Flow<Action<S, A>>,
    state: StateAccessor<S>,
    isInState: (S) -> Boolean
) {

    private enum class InternalStateChangedSubscription {
        ENTERED, LEFT, NOT_CHANGED
    }

    /**
     * Information about whether a state machine entered or did left a certain state
     */
    internal enum class StateChanged {
        /**
         * Entered the specified state
         */
        ENTERED,
        /**
         * Left the specified state
         */
        LEFT
    }

    private val mutex = Mutex()
    private var lastState: S? = null

    internal val flow: Flow<StateChanged> = actions.map {
        println("action $it")
        mutex.withLock {

            val state = state()
            val previousState = lastState
            val isInExpectedState = isInState(state)
            val previousStateInExpectedState = if (previousState == null) {
                false
            } else {
                isInState(previousState)
            }

            if (previousState == null) {
                if (isInExpectedState) {
                    InternalStateChangedSubscription.ENTERED
                } else {
                    InternalStateChangedSubscription.NOT_CHANGED
                }
            } else {
                when {
                    isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.NOT_CHANGED
                    isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.ENTERED
                    !isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.LEFT
                    !isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.NOT_CHANGED
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
        .filter { it != InternalStateChangedSubscription.NOT_CHANGED }
        .distinctUntilChanged()
        .map {
            when (it) {
                InternalStateChangedSubscription.ENTERED -> StateChanged.ENTERED
                InternalStateChangedSubscription.LEFT -> StateChanged.LEFT
                InternalStateChangedSubscription.NOT_CHANGED -> throw IllegalStateException(
                    "Internal Error occurred. File an issue on Github."
                )
            }
        }
}

internal fun <S: Any, A : Any> Flow<Action<S, A>>.mapStateChanges(
    stateAccessor: StateAccessor<S>,
    isInState : (S) -> Boolean
): Flow<MapStateChange.StateChanged> = MapStateChange<S, A>(
    actions = this,
    isInState = isInState,
    state = stateAccessor
).flow