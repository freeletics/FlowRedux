package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.CoroutineWaiter
import com.freeletics.flowredux.util.mapToIsInState
import com.freeletics.flowredux.util.whileInState
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
internal class OnEnterStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S : Any, A>(
    override val isInState: IsInState<S>,
    private val subStateMachineFactory: (InputState) -> StateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {

    override fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>> {
        return dispatchActionsToSubStateMachineAndCollectSubStateMachineState(actions, getState)
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatchActionsToSubStateMachineAndCollectSubStateMachineState(
        upstreamActions: Flow<Action<A>>,
        getState: GetState<S>,
    ): Flow<ChangedState<S>> {
        return upstreamActions
            .whileInState(isInState, getState) { actions: Flow<Action<A>> ->
                val stateOnEntering = getState() as? InputState
                if (stateOnEntering == null) {
                    emptyFlow() // somehow we left already the state but flow did not cancel yet
                } else {
                    // Used to synchronize the dispatching of actions to the sub statemachine
                    // by first waiting until the sub statemachine is actually collected
                    val coroutineWaiter = CoroutineWaiter()

                    // create sub statemachine via factory.
                    // Cleanup of instantiated sub statemachine reference is happening in .onComplete {...}
                    var subStateMachine: StateMachine<SubStateMachineState, SubStateMachineAction>? =
                        subStateMachineFactory(stateOnEntering)

                    // build the to be returned flow
                    actions
                        .onEach { action ->
                            if (action is ExternalWrappedAction<A>) {
                                // dispatch the incoming actions to the statemachine
                                coroutineScope {
                                    launch {
                                        coroutineWaiter.waitUntilResumed()
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
                                    subStateMachine?.state?.onStart {
                                        coroutineWaiter.resume() // once we start collecting state we can resume dispatching any waiting actions
                                    } ?: emptyFlow() // if null then flow has been canceled
                                }
                            } else {
                                // Safety net: stop collecting state of sub state machine
                                // This should never be called as the full flow should be canceled.
                                emptyFlow()
                            }.flatMapConcat { subStateMachineState ->
                                changeState(getState) { inputState ->
                                    stateMapper(State(inputState), subStateMachineState)
                                }
                            }
                        }
                        .onCompletion {
                            subStateMachine = null // clean up to avoid memory leaks
                        }
                }
            }
    }
}
