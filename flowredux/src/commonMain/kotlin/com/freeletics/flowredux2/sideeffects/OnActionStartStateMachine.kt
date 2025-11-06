package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.State
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.logD
import com.freeletics.flowredux2.logI
import com.freeletics.flowredux2.logV
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
internal class OnActionStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, TriggerAction : A, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val stateMachineFactoryBuilder: State<InputState>.(action: TriggerAction) -> FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
    internal val subActionClass: KClass<TriggerAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val cancelOnState: (SubStateMachineState) -> Boolean,
    private val handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    override val logger: TaggedLogger?,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return channelFlow {
            val actionBroadcast = MutableSharedFlow<A>()
            actions
                .collect { action ->
                    // broadcast to all existing state machines
                    actionBroadcast.emit(action)

                    // see if we need to start a new state machine
                    if (subActionClass.isInstance(action)) {
                        runOnlyIfInInputState(getState) { currentState ->
                            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))

                            logger.logD { "Starting sub state machine" }
                            @Suppress("UNCHECKED_CAST")
                            val stateMachine = ChangeableState(currentState)
                                .stateMachineFactoryBuilder(action as TriggerAction)
                                .launchIn(scope)

                            val awaitActionCollection = Channel<Unit>()
                            scope.launch {
                                actionBroadcast
                                    .onStart { awaitActionCollection.send(Unit) }
                                    .onEach {
                                        // cancel because same action was received again
                                        if (it == action) {
                                            logger.logD { "Cancelling sub state machine" }
                                            scope.cancel()
                                        }
                                    }
                                    .filter { !subActionClass.isInstance(it) }
                                    .onEach { logger.logV { "Forwarding $it" } }
                                    .mapNotNull(actionMapper)
                                    .collect { stateMachine.dispatch(it) }
                            }
                            // wait for the actionBroadcast to be collected to avoid following actions being dropped
                            // due to a race condition between the launch starting and the collect here already handling
                            // the next action
                            awaitActionCollection.receive()

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
                        }
                    }
                }
        }
    }
}
