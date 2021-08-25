package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.flow.mapToIsInState
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@FlowPreview
// TODO find better name: i.e. DelegateToStateMachineSideEffectBuilder?
class SubStateMachineSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S, A>(
    private val subStateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction,
    private val stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>,
    private val isInState: (S) -> Boolean
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions.subscribeToStateMachineStateAndDispatchActionsToIt(getState)
        }
    }

    private fun Flow<Action<S, A>>.subscribeToStateMachineStateAndDispatchActionsToIt(getState: GetState<S>): Flow<Action<S, A>> {
        val upstreamActions: Flow<Action<S, A>> = this
        return flow {
            coroutineScope {

                // collect the state of the sub state machine
                launch {
                    upstreamActions
                        .mapToIsInState(isInState, getState)
                        .flatMapLatest { inState: Boolean ->
                            if (inState) {
                                // start collecting state of sub state machine
                                subStateMachine.state
                            } else {
                                // stop collecting state of sub state machine
                                emptyFlow()
                            }
                        }.collect { subStateMachineState: SubStateMachineState ->

                            runOnlyIfInInputState(getState, isInState) { inputState ->

                                val changeStateAction = ChangeStateAction<S, A>(
                                    loggingInfo = "Sub StateMachine",
                                    runReduceOnlyIf = isInState,
                                    changeState = stateMapper(
                                        inputState,
                                        subStateMachineState
                                    )
                                )
                                emit(changeStateAction)
                            }
                        }
                }

                // Collect upstream actions and dispatch it to the sub state machine
                launch {
                    upstreamActions
                        .whileInState(isInState, getState) { actions: Flow<Action<S, A>> ->
                            actions
                                .filterIsInstance<ExternalWrappedAction<S, A>>()
                                .onEach { externalAction: ExternalWrappedAction<S, A> ->
                                    // TODO: do we need to launch this in another coroutine?
                                    subStateMachine.dispatch(actionMapper(externalAction.action))
                                }
                        }
                        .collect()
                }
            }
        }
    }
}

