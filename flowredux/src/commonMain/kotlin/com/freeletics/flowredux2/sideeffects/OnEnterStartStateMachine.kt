package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
internal class OnEnterStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S : Any, A>(
    override val isInState: IsInState<S>,
    private val subStateMachineFactory: FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return channelFlow {
            val subStateMachine = subStateMachineFactory.launchIn(this)
            launch {
                subStateMachine.state
                    .flatMapConcat { subStateMachineState ->
                        changeState(getState) { inputState ->
                            ChangeableState(inputState).handler(subStateMachineState)
                        }
                    }
                    .collect {
                        send(it)
                    }
            }

            actions
                .mapNotNull { actionMapper(it) }
                .collect { action ->
                    runOnlyIfInInputState(getState) {
                        subStateMachine.dispatch(action)
                    }
                }
        }
    }
}
