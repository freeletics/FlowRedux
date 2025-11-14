package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.ExecutionPolicy
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.logI
import com.freeletics.flowredux2.util.flatMapWithExecutionPolicy
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

@ExperimentalCoroutinesApi
internal class OnAction<InputState : S, SubAction : A, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    internal val subActionClass: KClass<SubAction>,
    internal val executionPolicy: ExecutionPolicy,
    internal val handler: suspend ChangeableState<InputState>.(action: SubAction) -> ChangedState<S>,
    override val logger: TaggedLogger?,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return actions.filterIsInstance(subActionClass)
            .flatMapWithExecutionPolicy(executionPolicy) { action ->
                logger.logI { "Handle $action" }
                changeState(getState) { inputState ->
                    ChangeableState(inputState).handler(action)
                }
            }
    }
}
