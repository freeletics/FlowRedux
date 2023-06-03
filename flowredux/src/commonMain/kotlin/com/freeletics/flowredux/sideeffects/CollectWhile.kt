package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.flatMapWithExecutionPolicy
import com.freeletics.flowredux.util.mapToIsInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@ExperimentalCoroutinesApi
internal class CollectWhile<T, InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val flow: Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {

    override fun produceState(actions: Flow<Action<S, A>>, getState: GetState<S>): Flow<ChangeStateAction<S, A>> {
        return actions
            .mapToIsInState(isInState, getState)
            .flatMapLatest { inState ->
                if (inState) {
                    flow.flatMapWithExecutionPolicy(executionPolicy) { item ->
                        changeState(getState) { snapshot ->
                            handler(item, State(snapshot))
                        }
                    }
                } else {
                    emptyFlow()
                }
            }
    }
}
