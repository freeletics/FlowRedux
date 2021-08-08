package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.CollectWhileInStateHandler
import com.freeletics.flowredux.dsl.FlatMapPolicy
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
@FlowPreview
@ExperimentalCoroutinesApi
internal class CollectInStateBasedOnStateBuilder<T, InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val cancelWhenStateChanges: Boolean,
    private val flowBuilder: (Flow<InputState>) -> Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val handler: CollectWhileInStateHandler<T, InputState, S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions.whileInState(isInState, getState, cancelWhenStateChanges) { inStateActions ->
                flowOfCurrentState(inStateActions, getState)
                    .transformWithFlowBuilder()
                    .flatMapWithPolicy(flatMapPolicy) {
                        setStateFlow(value = it, getState = getState)
                    }
            }
        }
    }

    @Suppress("unchecked_cast")
    private fun flowOfCurrentState(actions: Flow<Action<S, A>>, getState: GetState<S>): Flow<InputState> {
        // after every state change there is a guaranteed action emission so we use this
        // to get the current state
        return actions.map { getState() as InputState }
            // an action emission does not guarantee that the state changed so we need to filter
            // out multiple emissions of identical state objects
            .distinctUntilChanged()
    }

    private fun Flow<InputState>.transformWithFlowBuilder(): Flow<T> {
        return flowBuilder(this)
    }

    private suspend fun setStateFlow(
        value: T,
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {

        runOnlyIfInInputState(getState, isInState) { inputState ->
            val changeState = handler(value, inputState)
            emit(
                ChangeStateAction<S, A>(
                    loggingInfo = "collectWhileInState<>", // TODO better logging
                    changeState = changeState,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
    }
}
