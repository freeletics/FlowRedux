@file:Suppress("UNCHECKED_CAST")

package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangeState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@FlowPreview
@ExperimentalCoroutinesApi
internal class StartStateMachineOnActionInStateSideEffectBuilder<SubStateMachineState : Any, SubStateMachineAction : Any, InputState : S, ActionThatTriggeredStartingStateMachine : A, S : Any, A : Any>
(
    private val subStateMachineFactory: (action: ActionThatTriggeredStartingStateMachine, state: InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
    private val actionMapper: (A) -> SubStateMachineAction,
    private val stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>,
    private val isInState: (S) -> Boolean,
    internal val subActionClass: KClass<out A>,
    internal val executionPolicy: ExecutionPolicy,
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->

            actions.onEach { println("actions $it") }.whileInState(isInState, getState) { inStateAction ->
                channelFlow<Action<S, A>> {
                    val subStateMachinesMap = StateMachinesMap<SubStateMachineState, SubStateMachineAction, ActionThatTriggeredStartingStateMachine>()
                    val subStateMachinesMapMutex = Mutex()

                    inStateAction
                        .onEach { println("onEach $it ${getState()}") }
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

                                        if (subActionClass.isInstance(action.action)) {
                                            val actionThatStartsStateMachine =
                                                action.action as ActionThatTriggeredStartingStateMachine

                                            val stateMachine = subStateMachineFactory(
                                                actionThatStartsStateMachine, currentState
                                            )

                                            // Launch substatemachine
                                            val j = launch {
                                                stateMachine.state.collect { subStateMachineState ->
                                                    runOnlyIfInInputState(getState, isInState) { parentState ->
                                                        send(
                                                            ChangeStateAction(
                                                                runReduceOnlyIf = isInState,
                                                                changeState = stateMapper(parentState, subStateMachineState)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            println("job started $j")
                                            subStateMachinesMapMutex.withLock {
                                                subStateMachinesMap.add(
                                                    actionThatStartedStateMachine = actionThatStartsStateMachine,
                                                    stateMachine = stateMachine,
                                                    job = j
                                                )
                                            }
                                            println("Added to map")

                                        } else {
                                            // a regular action that needs to be forwarded
                                            // to the active sub state machine
                                            println("Other Action to dispatch to substatemachine $action")
                                            subStateMachinesMapMutex.withLock {
                                                // TODO should this be launched in its own coroutine?
                                                subStateMachinesMap.forEachStateMachine { stateMachine ->
                                                    println("Dispatching ${action.action}")
                                                    launch {
                                                        stateMachine.dispatch(
                                                            actionMapper(action.action as A)
                                                        )
                                                    }
                                                    println("Adter dispatched ${action.action}")
                                                }
                                            }
                                            println("After Mutex closed")
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
internal data class StateMachineAndJob<S : Any, A : Any>(
    val stateMachine: FlowReduxStateMachine<S, A>,
    val job: Job,
)

@ExperimentalCoroutinesApi
@FlowPreview
internal class StateMachinesMap<S : Any, A : Any, ActionThatTriggeredStartingStateMachine : Any> {
    private val stateMachinesAndJobsMap = LinkedHashMap<ActionThatTriggeredStartingStateMachine, MutableList<StateMachineAndJob<S, A>>>()

    fun add(actionThatStartedStateMachine: ActionThatTriggeredStartingStateMachine, stateMachine: FlowReduxStateMachine<S, A>, job: Job) {
        var existingStateMachinesAndJobs: MutableList<StateMachineAndJob<S, A>>? = stateMachinesAndJobsMap[actionThatStartedStateMachine]
        if (existingStateMachinesAndJobs == null) {
            existingStateMachinesAndJobs = mutableListOf()
            this.stateMachinesAndJobsMap[actionThatStartedStateMachine] = existingStateMachinesAndJobs
        }

        existingStateMachinesAndJobs.add(StateMachineAndJob(stateMachine = stateMachine, job = job))
    }

    suspend inline fun forEachStateMachine(crossinline block: suspend (FlowReduxStateMachine<S, A>) -> Unit) {
        stateMachinesAndJobsMap.values.forEach { stateMachinesAnJobs ->
            stateMachinesAnJobs.forEach {
                block(it.stateMachine)
            }
        }
    }

}
