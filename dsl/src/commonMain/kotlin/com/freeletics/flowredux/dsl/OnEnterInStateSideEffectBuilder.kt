package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

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
        return { actions: Flow<Action<S, A>>, state: StateAccessor<S> ->
            actions
                .mapStateChanges(isInState = isInState, stateAccessor = state)
                .filter { it == MapStateChange.StateChanged.ENTERED }
                .flatMapWithPolicy(flatMapPolicy) {
                    setStateFlow(state)
                }
        }
    }

    private suspend fun setStateFlow(
        stateAccessor: StateAccessor<S>
    ): Flow<Action<S, A>> = flow {

        val setState = SetStateImpl<S>(
            defaultRunIf = { state -> isInState(state) },
            invokeCallback = { runIf, reduce ->
                emit(
                    SelfReducableAction<S, A>(
                        loggingInfo = "onEnter<>", // TODO logging
                        reduce = reduce,
                        runReduceOnlyIf = runIf
                    )
                )
            }
        )
        block(stateAccessor, setState)
    }
}

typealias InStateOnEnterBlock<S> = suspend (getState: StateAccessor<S>, setState: SetState<S>) -> Unit

