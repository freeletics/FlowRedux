package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.FlowReduxDsl
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.dsl.flow.mapToIsInState
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@FlowPreview
internal class StartStatemachineOnEnterSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S : Any, A>(
    private val subStateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    private val isInState: (S) -> Boolean
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            dispatchActionsToSubStateMachineAndCollectSubStateMachineState(actions, getState)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatchActionsToSubStateMachineAndCollectSubStateMachineState(
        upstreamActions: Flow<Action<S, A>>,
        getState: GetState<S>
    ): Flow<Action<S, A>> {
        return upstreamActions
            .whileInState(isInState, getState) { actions: Flow<Action<S, A>> ->
                val stateOnEntering = getState() as? InputState
                if (stateOnEntering == null) {
                    emptyFlow() // somehow we left already the state but flow did not cancel yet
                } else {
                    // create sub statemachine via factory.
                    // Cleanup of instantiated sub statemachine reference is happening in .onComplete {...}
                    var subStateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>? =
                        subStateMachineFactory(stateOnEntering)

                    // build the to be returned flow
                    actions
                        .onEach { action ->
                            if (action is ExternalWrappedAction<S, A>) {
                                // dispatch the incoming actions to the statemachine
                                coroutineScope {
                                    launch {
                                        actionMapper(action.action)?.let {
                                        // safety net:
                                        // if sub statemachine is null then flow got canceled but
                                        // somehow this code still executes
                                            subStateMachine?.dispatch(it)
                                        }
                                    }
                                }
                            }
                        }
                        // we use mapToIsInState so that the downstream is actually
                        // executed only as we want to collect subStateMachine.state exactly once
                        .mapToIsInState(isInState, getState)
                        .flatMapLatest { inState: Boolean ->
                            if (inState) {
                                // start collecting state of sub state machine
                                val currentState = getState() as? InputState
                                if (currentState == null) {
                                    // Safety net: we have transitioned to another state
                                    emptyFlow()
                                } else {
                                    subStateMachine?.state
                                        ?: emptyFlow() // if null then flow has been canceled
                                }
                            } else {
                                // Safety net: stop collecting state of sub state machine
                                // This should never be called as the full flow should be canceled.
                                emptyFlow()
                            }.mapNotNull { subStateMachineState: SubStateMachineState ->
                                var changeStateAction: ChangeStateAction<S, A>? = null

                                runOnlyIfInInputState(getState, isInState) { inputState ->
                                    changeStateAction = ChangeStateAction<S, A>(
                                        runReduceOnlyIf = isInState,
                                        changedState = stateMapper(
                                            State(inputState),
                                            subStateMachineState
                                        )
                                    )
                                }

                                changeStateAction
                                // can be null if not in input state
                                // but null will be filtered out by .mapNotNull()
                            }
                        }
                        .onCompletion {
                            subStateMachine = null // clean up to avoid memory leaks
                        }
                }
            }


    }
}
