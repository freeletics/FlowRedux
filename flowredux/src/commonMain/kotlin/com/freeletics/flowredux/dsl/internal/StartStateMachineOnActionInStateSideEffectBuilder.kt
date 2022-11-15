@file:Suppress("UNCHECKED_CAST")

package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.FlowReduxDsl
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@FlowPreview
@ExperimentalCoroutinesApi
internal class StartStateMachineOnActionInStateSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, ActionThatTriggeredStartingStateMachine : A, S : Any, A : Any>
(
    private val subStateMachineFactory: (action: ActionThatTriggeredStartingStateMachine, state: InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction?,
    private val stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    private val isInState: (S) -> Boolean,
    internal val subActionClass: KClass<out A>,
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->

            actions.whileInState(isInState, getState) { inStateAction ->
                channelFlow<Action<S, A>> {
                    val subStateMachinesMap = SubStateMachinesMap<SubStateMachineState, SubStateMachineAction, ActionThatTriggeredStartingStateMachine>()

                    inStateAction
                        .collect { action ->
                            // collect upstream
                            when (action) {
                                is ChangeStateAction,
                                is InitialStateAction,
                                -> {
                                    // Nothing needs to be done, these Actions are not interesting for
                                    // this operator, so we can just safely ignore them
                                }
                                is ExternalWrappedAction<*, *> ->
                                    runOnlyIfInInputState(getState, isInState) { currentState ->
                                        // TODO take ExecutionPolicy into account
                                        if (subActionClass.isInstance(action.action)) {
                                            val actionThatStartsStateMachine =
                                                action.action as ActionThatTriggeredStartingStateMachine

                                            val stateMachine = subStateMachineFactory(
                                                actionThatStartsStateMachine, currentState
                                            )

                                            // Launch substatemachine
                                            val job = launch {
                                                stateMachine.state
                                                    .onCompletion { subStateMachinesMap.remove(stateMachine) }
                                                    .collect { subStateMachineState ->
                                                        runOnlyIfInInputState(getState, isInState) { parentState ->
                                                            send(
                                                                ChangeStateAction(
                                                                    runReduceOnlyIf = isInState,
                                                                    changedState = stateMapper(State(parentState), subStateMachineState)
                                                                )
                                                            )
                                                        }
                                                    }
                                            }
                                            subStateMachinesMap.cancelPreviousAndAddNew(
                                                actionThatStartedStateMachine = actionThatStartsStateMachine,
                                                stateMachine = stateMachine,
                                                job = job
                                            )

                                        } else {
                                            // a regular action that needs to be forwarded
                                            // to the active sub state machine
                                            subStateMachinesMap.forEachStateMachine { stateMachine ->
                                                launch {
                                                    actionMapper(action.action as A)?.let {
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
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    internal class SubStateMachinesMap<S : Any, A : Any, ActionThatTriggeredStartingStateMachine : Any> {
        @ExperimentalCoroutinesApi
        @FlowPreview
        internal data class StateMachineAndJob<S : Any, A : Any>(
            val stateMachine: FlowReduxStateMachine<S, A>,
            val job: Job,
        )

        private val mutex = Mutex()
        private val stateMachinesAndJobsMap = LinkedHashMap<ActionThatTriggeredStartingStateMachine, StateMachineAndJob<S, A>>()

        suspend fun size(): Int = mutex.withLock { stateMachinesAndJobsMap.size }

        suspend fun cancelPreviousAndAddNew(actionThatStartedStateMachine: ActionThatTriggeredStartingStateMachine, stateMachine: FlowReduxStateMachine<S, A>, job: Job) {
            mutex.withLock {
                val existingStateMachinesAndJobs: StateMachineAndJob<S, A>? = stateMachinesAndJobsMap[actionThatStartedStateMachine]
                existingStateMachinesAndJobs?.job?.cancel()

                stateMachinesAndJobsMap[actionThatStartedStateMachine] = StateMachineAndJob(stateMachine = stateMachine, job = job)
            }
        }

        suspend inline fun forEachStateMachine(crossinline block: suspend (FlowReduxStateMachine<S, A>) -> Unit) {
            mutex.withLock {
                stateMachinesAndJobsMap.values.forEach { stateMachineAndJob ->
                    block(stateMachineAndJob.stateMachine)
                }
            }
        }

        suspend fun remove(stateMachine: FlowReduxStateMachine<S, A>): StateMachineAndJob<S, A>? {
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
                } else
                    null
            }

            return result
        }
    }
}
