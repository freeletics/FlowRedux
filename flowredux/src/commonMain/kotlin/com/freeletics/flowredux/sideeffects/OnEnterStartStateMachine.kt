package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.CoroutineWaiter
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
internal class OnEnterStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S : Any, A>(
    override val isInState: IsInState<S>,
    private val subStateMachine: StateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return channelFlow {
            // Used to synchronize the dispatching of actions to the sub statemachine
            // by first waiting until the sub statemachine is actually collected
            val coroutineWaiter = CoroutineWaiter()

            launch {
                subStateMachine.state
                    // once we start collecting state we can resume dispatching any waiting actions
                    .onStart { coroutineWaiter.resume() }
                    .flatMapConcat { subStateMachineState ->
                        changeState(getState) { inputState ->
                            stateMapper(State(inputState), subStateMachineState)
                        }
                    }
                    .collect {
                        send(it)
                    }
            }

            coroutineWaiter.waitUntilResumed()
            actions.mapNotNull { actionMapper(it) }
                .collect { action ->
                    runOnlyIfInInputState(getState) {
                        subStateMachine.dispatch(action)
                    }
                }
        }
    }
}
