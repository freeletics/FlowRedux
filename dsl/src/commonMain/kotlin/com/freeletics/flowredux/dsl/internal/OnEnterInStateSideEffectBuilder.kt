package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A builder that generates a [SideEffect] that triggers every time the state machine enters
 * a certain state.
 */
class OnEnterInStateSideEffectBuilder<InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val block: InStateOnEnterBlock<InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .map { isInState(getState()) }
                .distinctUntilChanged()
                .flatMapLatest {
                    if (it) {
                        setStateFlow(getState)
                    } else {
                        flowOf()
                    }
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

