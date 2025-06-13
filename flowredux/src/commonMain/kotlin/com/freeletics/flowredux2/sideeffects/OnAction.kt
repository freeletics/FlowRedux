package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.ExecutionPolicy
import com.freeletics.flowredux2.State
import com.freeletics.flowredux2.util.flatMapWithExecutionPolicy
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

@ExperimentalCoroutinesApi
internal class OnAction<InputState : S, SubAction : A, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    internal val subActionClass: KClass<SubAction>,
    internal val executionPolicy: ExecutionPolicy,
    internal val handler: suspend (action: SubAction, state: State<InputState>) -> ChangedState<S>,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return actions.asSubAction()
            .flatMapWithExecutionPolicy(executionPolicy) { action ->
                changeState(getState) { inputState ->
                    handler(action, State(inputState))
                }
            }
    }

    @Suppress("unchecked_cast")
    private fun Flow<A>.asSubAction(): Flow<SubAction> {
        return mapNotNull { action ->
            action.takeIf { subActionClass.isInstance(it) } as? SubAction
        }
    }
}
