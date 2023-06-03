package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.CoroutineWaiter
import com.freeletics.flowredux.util.whileInState
import com.freeletics.mad.statemachine.StateMachine
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OnActionStartStateMachine<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, ActionThatTriggeredStartingStateMachine : A, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val subStateMachineFactory: (
        action: ActionThatTriggeredStartingStateMachine,
        state: InputState,
    ) -> StateMachine<SubStateMachineState, SubStateMachineAction>,
    internal val subActionClass: KClass<out A>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {

    @Suppress("UNCHECKED_CAST")
    override fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>> {
        return actions.whileInState(isInState, getState) { inStateAction ->
            channelFlow {
                val subStateMachinesMap = SubStateMachinesMap<SubStateMachineState, SubStateMachineAction, ActionThatTriggeredStartingStateMachine>()

                inStateAction
                    .collect { action ->
                        // collect upstream
                        when (action) {
                            is InitialStateAction,
                            -> {
                                // Nothing needs to be done, these Actions are not interesting for
                                // this operator, so we can just safely ignore them
                            }
                            is ExternalWrappedAction ->
                                runOnlyIfInInputState(getState) { currentState ->
                                    // TODO take ExecutionPolicy into account
                                    if (subActionClass.isInstance(action.action)) {
                                        val actionThatStartsStateMachine =
                                            action.action as ActionThatTriggeredStartingStateMachine

                                        val stateMachine = subStateMachineFactory(
                                            actionThatStartsStateMachine,
                                            currentState,
                                        )

                                        // Launch substatemachine
                                        val coroutineWaiter = CoroutineWaiter()
                                        val job = launch {
                                            stateMachine.state
                                                .onStart {
                                                    coroutineWaiter.resume() // Resume waiting coroutines that dispatch Actions
                                                }
                                                .onCompletion {
                                                    subStateMachinesMap.remove(stateMachine)
                                                }
                                                .collect { subStateMachineState ->
                                                    runOnlyIfInInputState(getState) { parentState ->
                                                        val changedState = stateMapper(State(parentState), subStateMachineState)
                                                        send(changedState)
                                                    }
                                                }
                                        }
                                        subStateMachinesMap.cancelPreviousAndAddNew(
                                            actionThatStartedStateMachine = actionThatStartsStateMachine,
                                            stateMachine = stateMachine,
                                            job = job,
                                            coroutineWaiter = coroutineWaiter,
                                        )
                                    } else {
                                        // a regular action that needs to be forwarded
                                        // to the active sub state machine
                                        subStateMachinesMap.forEachStateMachine { stateMachine, coroutineWaiter ->
                                            launch {
                                                coroutineWaiter.waitUntilResumed() // Wait until sub statemachine's state Flow is collected
                                                actionMapper(action.action)?.let {
                                                    stateMachine.dispatch(it)
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

    internal class SubStateMachinesMap<S : Any, A : Any, ActionThatTriggeredStartingStateMachine : Any> {
        internal data class StateMachineAndJob<S : Any, A : Any>(
            internal val stateMachine: StateMachine<S, A>,
            internal val job: Job,
            internal val coroutineWaiter: CoroutineWaiter,
        )

        private val mutex = Mutex()
        private val stateMachinesAndJobsMap = LinkedHashMap<ActionThatTriggeredStartingStateMachine, StateMachineAndJob<S, A>>()

        suspend fun size(): Int = mutex.withLock { stateMachinesAndJobsMap.size }

        suspend fun cancelPreviousAndAddNew(
            actionThatStartedStateMachine: ActionThatTriggeredStartingStateMachine,
            stateMachine: StateMachine<S, A>,
            coroutineWaiter: CoroutineWaiter,
            job: Job,
        ) {
            mutex.withLock {
                val existingStateMachinesAndJobs: StateMachineAndJob<S, A>? = stateMachinesAndJobsMap[actionThatStartedStateMachine]
                existingStateMachinesAndJobs?.job?.cancel()

                stateMachinesAndJobsMap[actionThatStartedStateMachine] = StateMachineAndJob(
                    stateMachine = stateMachine,
                    job = job,
                    coroutineWaiter = coroutineWaiter,
                )
            }
        }

        suspend inline fun forEachStateMachine(
            crossinline block: suspend (StateMachine<S, A>, CoroutineWaiter) -> Unit,
        ) {
            mutex.withLock {
                stateMachinesAndJobsMap.values.forEach { stateMachineAndJob ->
                    block(stateMachineAndJob.stateMachine, stateMachineAndJob.coroutineWaiter)
                }
            }
        }

        suspend fun remove(stateMachine: StateMachine<S, A>): StateMachineAndJob<S, A>? {
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
