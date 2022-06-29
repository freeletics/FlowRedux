package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.dsl.flow.flatMapWithExecutionPolicy
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
@FlowPreview
@ExperimentalCoroutinesApi
internal class CollectInStateBasedOnStateBuilder<T, InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flowBuilder: (Flow<InputState>) -> Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions.whileInState(isInState, getState) { inStateActions ->
                flowOfCurrentState(inStateActions, getState)
                    .transformWithFlowBuilder()
                    .flatMapWithExecutionPolicy(executionPolicy) {
                        setStateFlow(value = it, getState = getState)
                    }
            }
        }

    }

    @Suppress("unchecked_cast")
    private fun flowOfCurrentState(
        actions: Flow<Action<S, A>>,
        getState: GetState<S>
    ): Flow<InputState> {
        // after every state change there is a guaranteed action emission so we use this
        // to get the current state
        return actions.mapNotNull { getState() as? InputState }
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
            val changeState = handler(value, State(inputState))
            emit(
                ChangeStateAction<S, A>(
                    changedState = changeState,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
    }
}
