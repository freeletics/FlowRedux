package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.logD
import com.freeletics.flowredux2.logI
import com.freeletics.flowredux2.logV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
internal class OnEnterStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, S : Any, A>(
    override val isInState: IsInState<S>,
    private val subStateMachineFactory: FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val cancelOnState: (SubStateMachineState) -> Boolean,
    private val handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    override val logger: TaggedLogger?,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return channelFlow {
            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))

            logger.logD { "Starting sub state machine" }
            val stateMachine = subStateMachineFactory.launchIn(scope)

            scope.launch {
                stateMachine.state.collect {
                    runOnlyIfInInputState(getState) { parentState ->
                        logger.logI { "Received sub state machine state $parentState" }
                        val changedState = ChangeableState(parentState).handler(it)
                        send(changedState)
                        if (cancelOnState(it)) {
                            logger.logD { "Cancelling sub state machine" }
                            scope.cancel()
                        }
                    }
                }
            }

            actions
                .mapNotNull(actionMapper)
                .onEach { logger.logV { "Forwarding $it" } }
                .collect { stateMachine.dispatch(it) }
        }
    }
}
