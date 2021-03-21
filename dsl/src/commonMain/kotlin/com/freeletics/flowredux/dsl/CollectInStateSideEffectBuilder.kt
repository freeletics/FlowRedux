package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
internal class CollectInStateSideEffectBuilder<T, InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .mapStateChanges(getState = getState, isInState = isInState)
                .flatMapWithPolicy(flatMapPolicy) { stateChange ->
                    when (stateChange) {
                        MapStateChange.StateChanged.ENTERED ->
                            flow.flatMapLatest {
                                // TODO is it actually always flatMapLatest or also flatMapWithPolicy
                                setStateFlow(value = it, getState = getState)
                            }
                        MapStateChange.StateChanged.LEFT -> flow { }
                    }
                }
        }
    }

    private suspend fun setStateFlow(
        value: T,
        getState: GetState<S>
    ): Flow<Action<S, A>> =
        flow {

            runOnlyIfInInputState(getState, isInState) { inputState ->
                val changeState = block(value, inputState)
                emit(
                    ChangeStateAction<S, A>(
                        loggingInfo = "collectWhileInState<>", // TODO logging
                        changeState = changeState,
                        runReduceOnlyIf = { state -> isInState(state) }
                    )
                )
            }
        }
}

typealias InStateObserverBlock<T, InputState, S> = suspend (value: T, state: InputState) -> ChangeState<S>