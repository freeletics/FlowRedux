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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@FlowPreview
@ExperimentalCoroutinesApi
internal class StartStateMachineOnActionInStateSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, ActionThatTriggeredStartingStateMachine : A, S : Any, A : Any>
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
                channelFlow<Action<S, A>> {

                    inStateAction.collect { action ->
                        // collect upstream
                        when (action) {
                            is ChangeStateAction,
                            is InitialStateAction,
                            -> {
                                // Nothing needs to be done, these Actions are not interesting for
                                // this operator, so we can just safely ignore them
                            }
                            is ExternalWrappedAction<*, *> ->
                                runOnlyIfInInputState(getState, isInState) { currentState ->


                                    if (subActionClass.isInstance(action.action)) {
                                        val actionThatStartsStateMachine =
                                            action.action as ActionThatTriggeredStartingStateMachine

                                        val stateMachine = subStateMachineFactory(
                                            actionThatStartsStateMachine, currentState
                                        )

                                        // Launch substatemachine
                                        coroutineScope {
                                            launch {
                                                stateMachine.state.collect { subStateMachineState ->
                                                    runOnlyIfInInputState(getState, isInState) { parentState ->
                                                        send(
                                                            ChangeStateAction(
                                                                runReduceOnlyIf = isInState,
                                                                changeState = stateMapper(parentState, subStateMachineState)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                    } else {
                                        // a regular action that needs to be forwarded
                                        // to the active sub state machine
                                        

                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

/*
data class StateMachineAndJob<S : Any, A : Any>(
    val
    val stateMachines: FlowReduxStateMachine<S, A>,
    val job =,
)
*/
