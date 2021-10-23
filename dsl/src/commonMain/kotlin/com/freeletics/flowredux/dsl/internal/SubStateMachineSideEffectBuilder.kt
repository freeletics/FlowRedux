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
@Suppress("UNCHECKED_CAST")
class SubStateMachineSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S, A>(
    private val subStateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction,
    private val stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>,
    private val isInState: (S) -> Boolean
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            dispatchActionsToSubStateMachineAndCollectSubStateMachineState(actions, getState)
        }
    }

    private fun dispatchActionsToSubStateMachineAndCollectSubStateMachineState(
        upstreamActions: Flow<Action<S, A>>,
        getState: GetState<S>
    ): Flow<Action<S, A>> {
        return upstreamActions
            .whileInState(isInState, getState) { actions: Flow<Action<S, A>> ->
                val stateOnEntering = getState() as? InputState
                if (stateOnEntering == null) {
                    emptyFlow()
                } else {
                    var subStateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>? =
                        subStateMachineFactory(stateOnEntering)
                    actions
                        .onEach { action ->
                            if (action is ExternalWrappedAction<S, A>) {
                                coroutineScope {
                                    launch {
                                        subStateMachine?.dispatch(actionMapper(action.action))
                                    }
                                }
                            }
                        }
                        .mapToIsInState(isInState, getState)
                        .flatMapLatest { inState: Boolean ->
                            if (inState) {
                                // start collecting state of sub state machine
                                val currentState = getState() as? InputState
                                if (currentState == null) {
                                    emptyFlow()
                                } else {
                                    subStateMachine?.state
                                        ?: emptyFlow() // if null then flow has been canceled
                                }
                            } else {
                                // stop collecting state of sub state machine
                                emptyFlow()
                            }.mapNotNull { subStateMachineState: SubStateMachineState ->
                                var changeStateAction: ChangeStateAction<S, A>? = null
                                runOnlyIfInInputState(getState, isInState) { inputState ->

                                    changeStateAction = ChangeStateAction<S, A>(
                                        loggingInfo = "Sub StateMachine",
                                        runReduceOnlyIf = isInState,
                                        changeState = stateMapper(
                                            inputState,
                                            subStateMachineState
                                        )
                                    )
                                }
                                changeStateAction // can be null if not in input state
                            }
                        }
                        .onCompletion {
                            subStateMachine = null // clean up to avoid memory leaks
                        }
                }
            }


    }
}

