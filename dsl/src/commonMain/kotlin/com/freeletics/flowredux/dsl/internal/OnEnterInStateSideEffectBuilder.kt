package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.OnEnterHandler
import com.freeletics.flowredux.dsl.flow.mapToIsInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * A builder that generates a [SideEffect] that triggers every time the state machine enters
 * a certain state.
 */
@FlowPreview
@ExperimentalCoroutinesApi
class OnEnterInStateSideEffectBuilder<InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val handler: OnEnterHandler<InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .mapToIsInState(isInState, getState)
                .flatMapLatest {
                    if (it) {
                        setStateFlow(getState)
                    } else {
                        emptyFlow()
                    }
                }
        }
    }

    private suspend fun setStateFlow(
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {

        runOnlyIfInInputState(getState, isInState) { inputState ->
            val changeState = handler.handle(inputState)
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
