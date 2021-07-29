package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.FlatMapPolicy
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

/**
 * A builder that generates a [SideEffect] that triggers every time the state machine enters
 * a certain state.
 */
class OnEnterInStateSideEffectBuilder<InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateOnEnterBlock<InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    @FlowPreview
    @ExperimentalCoroutinesApi
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

        runOnlyIfInInputState(getState, isInState) { inputState ->
            val changeState = block(inputState)
            emit(
                ChangeStateAction<S, A>(
                    loggingInfo = "onEnter<>", // TODO logging
                    changeState = changeState,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
    }
}

typealias InStateOnEnterBlock<InputState, S> = suspend (state: InputState) -> ChangeState<S>

