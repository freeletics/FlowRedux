package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.flatMapWithExecutionPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
internal class CollectWhile<T, InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val flow: Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return flow.flatMapWithExecutionPolicy(executionPolicy) { item ->
            changeState(getState) { inputState ->
                handler(item, State(inputState))
            }
        }
    }
}
