package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.FlowReduxStateMachine
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.State
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ExperimentalCoroutinesApi
internal class OnActionStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, TriggerAction : A, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val stateMachineFactoryBuilder: State<InputState>.(action: TriggerAction) -> FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
    internal val subActionClass: KClass<out A>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
) : ActionBasedSideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return channelFlow {
            val subStateMachinesMap = SubStateMachinesMap<SubStateMachineState, SubStateMachineAction, TriggerAction>()
            actions.collect { action ->
                runOnlyIfInInputState(getState) { currentState ->
                    // TODO take ExecutionPolicy into account
                    if (subActionClass.isInstance(action)) {
                        @Suppress("UNCHECKED_CAST")
                        val actionThatStartsStateMachine = action as TriggerAction

                        val stateMachine = ChangeableState(currentState)
                            .stateMachineFactoryBuilder(actionThatStartsStateMachine)
                            .launchIn(this)

                        // Launch substatemachine
                        val job = launch {
                            stateMachine.state
                                .onCompletion {
                                    subStateMachinesMap.remove(stateMachine)
                                }
                                .collect { subStateMachineState ->
                                    runOnlyIfInInputState(getState) { parentState ->
                                        val changedState = ChangeableState(parentState).handler(subStateMachineState)
                                        send(changedState)
                                    }
                                }
                        }
                        subStateMachinesMap.cancelPreviousAndAddNew(
                            actionThatStartedStateMachine = actionThatStartsStateMachine,
                            stateMachine = stateMachine,
                            job = job,
                        )
                    } else {
                        // a regular action that needs to be forwarded
                        // to the active sub state machine
                        subStateMachinesMap.forEachStateMachine { stateMachine ->
                            launch {
                                actionMapper(action)?.let {
                                    stateMachine.dispatch(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    internal class SubStateMachinesMap<S : Any, A : Any, ActionThatTriggeredStartingStateMachine : Any> {
        internal data class StateMachineAndJob<S : Any, A : Any>(
            internal val stateMachine: FlowReduxStateMachine<StateFlow<S>, A>,
            internal val job: Job,
        )

        private val mutex = Mutex()
        private val stateMachinesAndJobsMap = LinkedHashMap<ActionThatTriggeredStartingStateMachine, StateMachineAndJob<S, A>>()

        suspend fun size(): Int = mutex.withLock { stateMachinesAndJobsMap.size }

        suspend fun cancelPreviousAndAddNew(
            actionThatStartedStateMachine: ActionThatTriggeredStartingStateMachine,
            stateMachine: FlowReduxStateMachine<StateFlow<S>, A>,
            job: Job,
        ) {
            mutex.withLock {
                val existingStateMachinesAndJobs: StateMachineAndJob<S, A>? = stateMachinesAndJobsMap[actionThatStartedStateMachine]
                existingStateMachinesAndJobs?.job?.cancel()

                stateMachinesAndJobsMap[actionThatStartedStateMachine] = StateMachineAndJob(
                    stateMachine = stateMachine,
                    job = job,
                )
            }
        }

        suspend inline fun forEachStateMachine(
            crossinline block: suspend (FlowReduxStateMachine<StateFlow<S>, A>) -> Unit,
        ) {
            mutex.withLock {
                stateMachinesAndJobsMap.values.forEach { stateMachineAndJob ->
                    block(stateMachineAndJob.stateMachine)
                }
            }
        }

        suspend fun remove(stateMachine: FlowReduxStateMachine<StateFlow<S>, A>): StateMachineAndJob<S, A>? {
            // could be optimized for better runtime
            val result = mutex.withLock {
                var key: ActionThatTriggeredStartingStateMachine? = null
                for ((actionThatTriggeredStarting, stateMachineAndJob) in stateMachinesAndJobsMap) {
                    if (stateMachineAndJob.stateMachine === stateMachine) {
                        key = actionThatTriggeredStarting
                        break
                    }
                }

                if (key != null) {
                    stateMachinesAndJobsMap.remove(key)
                } else {
                    null
                }
            }

            return result
        }
    }
}
