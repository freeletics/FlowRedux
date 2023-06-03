@file:Suppress("UNCHECKED_CAST")

package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.flatMapWithExecutionPolicy
import com.freeletics.flowredux.util.whileInState
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
) : SideEffect<InputState, S, A>() {

    override fun produceState(actions: Flow<Action<S, A>>, getState: GetState<S>): Flow<ChangeStateAction<S, A>> {
        return actions.whileInState(isInState, getState) { inStateAction ->
            inStateAction.mapNotNull {
                when (it) {
                    is ExternalWrappedAction<*, *> -> if (subActionClass.isInstance(it.action)) {
                        it.action as SubAction
                    } else {
                        null
                    }
                    is ChangeStateAction -> null
                    is InitialStateAction -> null
                }
            }
                .flatMapWithExecutionPolicy(executionPolicy) { action ->
                    changeState(getState) { snapshot ->
                        handler(action, State(snapshot))
                    }
                }
        }
    }
}
