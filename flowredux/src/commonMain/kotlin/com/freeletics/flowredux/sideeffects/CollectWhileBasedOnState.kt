package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.flatMapWithExecutionPolicy
import com.freeletics.flowredux.util.whileInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

@ExperimentalCoroutinesApi
internal class CollectWhileBasedOnState<T, InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val flowBuilder: (Flow<InputState>) -> Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
) : LegacySideEffect<InputState, S, A>() {

    override fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>> {
        return actions.whileInState(isInState, getState) { inStateActions ->
            flowOfCurrentState(inStateActions, getState)
                .transformWithFlowBuilder()
                .flatMapWithExecutionPolicy(executionPolicy) { item ->
                    changeState(getState) { snapshot ->
                        handler(item, State(snapshot))
                    }
                }
        }
    }

    @Suppress("unchecked_cast")
    private fun flowOfCurrentState(
        actions: Flow<Action<A>>,
        getState: GetState<S>,
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
}
