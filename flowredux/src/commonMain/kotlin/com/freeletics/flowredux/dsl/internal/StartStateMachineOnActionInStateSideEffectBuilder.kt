@file:Suppress("UNCHECKED_CAST")
package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.OnActionHandler
import com.freeletics.flowredux.dsl.flow.flatMapWithExecutionPolicy
import com.freeletics.flowredux.dsl.flow.mapToIsInState
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@FlowPreview
@ExperimentalCoroutinesApi
internal class StartStateMachineOnActionInStateSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, ActionThatTriggeredStartingStateMachine: A, S : Any, A : Any>
(
    private val subStateMachineFactory: (action: ActionThatTriggeredStartingStateMachine, state: InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction,
    private val stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>,
    private val isInState: (S) -> Boolean,
    internal val subActionClass: KClass<out A>,
    internal val executionPolicy: ExecutionPolicy,
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->

            actions.whileInState(isInState, getState) { inStateAction ->

                inStateAction.mapNotNull<Action<S,A>, ActionThatTriggeredStartingStateMachine> {
                    println("Action1 $it")
                    when (it) {
                        is ExternalWrappedAction<*, *> -> if (subActionClass.isInstance(it.action)) {
                            it.action as ActionThatTriggeredStartingStateMachine
                        } else {
                            null
                        }
                        is ChangeStateAction -> null
                        is InitialStateAction -> null
                    }
                }.flatMapMerge { actionThatStartsStateMachine : ActionThatTriggeredStartingStateMachine ->

                    // TODO take ExecutionPolicy

                    val stateOnEntering = getState() as? InputState
                    if (stateOnEntering == null) {
                        emptyFlow() // somehow we left already the state but flow did not cancel yet
                    } else {
                        // create sub statemachine via factory.
                        // Cleanup of instantiated sub statemachine reference is happening in .onComplete {...}
                        var subStateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>? =
                            subStateMachineFactory(actionThatStartsStateMachine, stateOnEntering)

                        println("StateMachine created from Factory $subStateMachine")
                        inStateAction.onEach { action ->
                            // Forward all incoming actions to the sub-statemachine

                            if (action is ExternalWrappedAction<S, A>) {
                                // dispatch the incoming actions to the sub-statemachine
                                coroutineScope {
                                    launch {
                                        // safety net:
                                        // if sub statemachine is null then flow got canceled but
                                        // somehow this code still executes
                                        subStateMachine?.dispatch(actionMapper(action.action))
                                    }
                                }
                            }
                        }
                            .mapToIsInState(isInState, getState)
                            .flatMapMerge { isInState: Boolean ->
                                if (!isInState) {
                                    emptyFlow() // No longer in state --> cancel
                                } else {
                                    subStateMachine?.state  // start collecting substatemachine
                                        ?: emptyFlow()  // safety net, should never happen
                                }
                            }
                            .mapNotNull { subStateMachineState: SubStateMachineState ->
                                var changeStateAction: ChangeStateAction<S, A>? = null

                                runOnlyIfInInputState(getState, isInState) { inputState ->
                                    changeStateAction = ChangeStateAction<S, A>(
                                        runReduceOnlyIf = isInState,
                                        changeState = stateMapper(
                                            inputState,
                                            subStateMachineState
                                        )
                                    )
                                }

                                changeStateAction
                                // can be null if not in input state (safety net, should never happen)
                                // but null will be filtered out by .mapNotNull()
                            }
                            .onCompletion { subStateMachine = null }

                    }

                    emptyFlow()
                }
            }
        }
    }
}
