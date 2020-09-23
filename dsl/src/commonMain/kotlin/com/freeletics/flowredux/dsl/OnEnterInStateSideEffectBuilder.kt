package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

/**
 * A builder that generates a [SideEffect] that triggers every time the state machine enters
 * a certain state.
 */
class OnEnterInStateSideEffectBuilder<S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateOnEnterBlock<S>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .mapStateChanges(isInState = isInState, getState = getState)
                .filter { it == MapStateChange.StateChanged.ENTERED }
                .flatMapWithPolicy(flatMapPolicy) {
                    setStateFlow(getState)
                }
        }
    }

    private suspend fun setStateFlow(
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {

        val setState = SetStateImpl<S>(
            defaultRunIf = { state -> isInState(state) },
            invokeCallback = { runIf, reduce ->
                emit(
                    SetStateAction<S, A>(
                        loggingInfo = "onEnter<>", // TODO logging
                        reduce = reduce,
                        runReduceOnlyIf = runIf
                    )
                )
            }
        )
        block(getState, setState)
    }
}

typealias InStateOnEnterBlock<S> = suspend (getState: GetState<S>, setState: SetState<S>) -> Unit

