package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.ExecutionPolicy
import com.freeletics.flowredux2.util.flatMapWithExecutionPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
internal class CollectWhile<T, InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val flow: Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: ChangeableState<InputState>) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return flow.flatMapWithExecutionPolicy(executionPolicy) { item ->
            changeState(getState) { inputState ->
                handler(item, ChangeableState(inputState))
            }
        }
    }
}
