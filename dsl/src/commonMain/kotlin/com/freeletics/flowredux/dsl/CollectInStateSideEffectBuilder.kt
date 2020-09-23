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
internal class CollectInStateSideEffectBuilder<T, S : Any, A : Any>(
    private val isInState : (S) -> Boolean,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, S>
) : InStateSideEffectBuilder<S, A>() {

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
            val setState = SetStateImpl<S>(
                defaultRunIf = { state -> isInState(state) },
                invokeCallback = { runIf, reduce ->
                    emit(
                        SetStateAction<S, A>(
                            loggingInfo = "collectWhileInState<>", // TODO logging
                            reduce = reduce,
                            runReduceOnlyIf = runIf
                        )
                    )
                }
            )
            block(value, getState, setState)
        }
}

typealias InStateObserverBlock<T, S> = suspend (value: T, getState: GetState<S>, setState: SetState<S>) -> Unit