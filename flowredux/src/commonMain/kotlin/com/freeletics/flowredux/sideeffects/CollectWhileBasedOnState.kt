package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.flatMapWithExecutionPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

@ExperimentalCoroutinesApi
internal class CollectWhileBasedOnState<T, InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val flowBuilder: (Flow<InputState>) -> Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {

    private val states = Channel<InputState>()

    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return states.receiveAsFlow()
            .transformWithFlowBuilder()
            .flatMapWithExecutionPolicy(executionPolicy) { item ->
                changeState(getState) { inputState ->
                    handler(item, State(inputState))
                }
            }
    }

    @Suppress("unchecked_cast")
    override suspend fun sendState(state: S) {
        states.send(state as InputState)
    }

    private fun Flow<InputState>.transformWithFlowBuilder(): Flow<T> {
        return flowBuilder(this)
    }
}
