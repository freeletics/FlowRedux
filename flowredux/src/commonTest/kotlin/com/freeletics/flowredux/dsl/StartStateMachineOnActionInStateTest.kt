package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.suspendTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class StartStateMachineOnActionInStateTest {

    @Test
    fun `child statemachine emits initial state to parent state machine`() = suspendTest {
        var childStateChanged = 0
        val child = StateMachine(initialState = TestState.S3)
        val parentStateMachine = StateMachine {
            inState<TestState.Initial> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { _, childState ->
                    childStateChanged++
                    OverrideState(childState)
                }
            }
        }

        parentStateMachine.state.test {
            assertEquals(TestState.Initial, awaitItem()) // parent initial state
            parentStateMachine.dispatch(TestAction.A1)
            assertEquals(TestState.S3, awaitItem()) // child initial state
            assertEquals(1, childStateChanged)
            expectNoEvents()
        }
    }

    @Test
    fun `child state machine stops after leaving While In State`() = suspendTest {
        var childStateChanged = 0
        val child = StateMachine(initialState = TestState.S3)
        val parentStateMachine = StateMachine {
            inState<TestState.Initial> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { _, _ ->
                    childStateChanged++
                    OverrideState(TestState.S1)
                }
            }
        }


        parentStateMachine.state.test {
            assertEquals(TestState.Initial, awaitItem()) // parent initial state
            assertEquals(child.stateFlowStarted, 0)
            assertEquals(child.stateFlowCompleted, 0)
            parentStateMachine.dispatch(TestAction.A1)
            assertEquals(TestState.S1, awaitItem()) // child initial state causes state transition
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 1)
            assertEquals(1, childStateChanged)
            expectNoEvents()
        }
    }

    @Test
    fun `actions are forwarded to the sub statemachine and sub state is propagated back ONLY while in state`() = suspendTest {
        var childStateChanged = 0
        var childS3A2Handeld = 0
        var childS1A2Handled = 0
        val recordedSubStates = mutableListOf<TestState>()

        val child = StateMachine(initialState = TestState.S3) {
            inState<TestState.S3> {
                on<TestAction.A2> { _, _ ->
                    childS3A2Handeld++
                    OverrideState(TestState.S1) // Doesn't really matter which state, parent ignores it anyway
                }
            }
            inState<TestState.S1> {
                on<TestAction.A2> { _, _ ->
                    childS1A2Handled++
                    OverrideState(TestState.S3)
                }
            }
        }

        val parentStateMachine = StateMachine(initialState = TestState.CounterState(0)) {
            inState<TestState.CounterState> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { _, childState ->
                    childStateChanged++
                    recordedSubStates += childState
                    MutateState<TestState.CounterState, TestState> { copy(counter = this.counter + 1) }
                }

                on<TestAction.A3> { _, _ ->
                    OverrideState(TestState.S3)
                }
            }
        }

        parentStateMachine.state.test {
            assertEquals(TestState.CounterState(0), awaitItem()) // parent initial state
            parentStateMachine.dispatch(TestAction.A1) // starts child
            assertEquals(TestState.CounterState(1), awaitItem()) // initial state of substatemachine caused this change
            assertEquals(1, childStateChanged)
            assertEquals<List<TestState>>(listOf(TestState.S3), recordedSubStates)

            parentStateMachine.dispatch(TestAction.A2) // dispatch Action to child state machine
            assertEquals(TestState.CounterState(2), awaitItem()) // state change because of A2
            assertEquals(1, childS3A2Handeld)
            assertEquals(0, childS1A2Handled)
            assertEquals(2, childStateChanged)
            assertEquals<List<TestState>>(listOf(TestState.S3, TestState.S1), recordedSubStates)


            parentStateMachine.dispatch(TestAction.A2) // dispatch Action to child state machine
            assertEquals(TestState.CounterState(3), awaitItem()) // state change because of A2
            assertEquals(1, childS3A2Handeld)
            assertEquals(1, childS1A2Handled)
            assertEquals(3, childStateChanged)
            assertEquals<List<TestState>>(listOf(TestState.S3, TestState.S1, TestState.S3), recordedSubStates)


            parentStateMachine.dispatch(TestAction.A3) // dispatch Action to parent state machine, causes state change
            assertEquals(TestState.S3, awaitItem())

            // Child state machine should have stopped because we are in S3 state
            parentStateMachine.dispatchAsync(TestAction.A2) // should not be handled by child statemaching
            delay(50)
            // verify child state machine had no interactions
            assertEquals(1, childS3A2Handeld)
            assertEquals(1, childS1A2Handled)
            assertEquals(3, childStateChanged)

            expectNoEvents()
        }

    }

    @Test
    fun `sub statmachine factory is invoked on re-enter and action and state mapper are invoked`() = suspendTest {

        val actionMapperRecordings = mutableListOf<TestAction>()
        val factoryParamsRecordings = mutableListOf<Pair<TestAction, TestState>>()
        val stateMapperRecordings = mutableListOf<Pair<TestState, TestState>>()
        val initialState = TestState.CounterState(0)

        val child = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A3> { _, _ -> OverrideState(TestState.S2) }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { _, _ -> OverrideState(TestState.S3) }
            }
        }
        val parent = StateMachine(initialState = initialState) {
            inState<TestState.CounterState> {
                onActionStartStateMachine<TestAction.A4, TestState, TestAction>(
                    stateMachineFactory = { action, inputState ->
                        factoryParamsRecordings += pairOf(action, inputState)
                        child
                    },
                    actionMapper = {
                        actionMapperRecordings += it
                        it
                    },
                    stateMapper = { inputState, subState ->
                        stateMapperRecordings += pairOf(inputState, subState)
                       if (subState is TestState.S3)
                            OverrideState(TestState.S3)
                        else
                            MutateState<TestState.CounterState, TestState> {
                                copy(counter = this.counter + 1)
                            }
                    }
                )
            }
        }

        parent.state.test {
            //
            // Initial setup an initial state emission checks
            //
            assertEquals(TestState.CounterState(0), awaitItem()) // initial state
            assertEquals(emptyList(), factoryParamsRecordings) // factory should not have been invoked
            assertEquals(child.stateFlowStarted, 0)

            //
            // Dispatch action to start child state machine and check factory
            //
            parent.dispatch(TestAction.A4(1))
            assertEquals(TestState.CounterState(1), awaitItem()) // initial emission of child
            assertEquals(  // factory should have been invoked
                listOf<Pair<TestAction, TestState>>(
                    pairOf(TestAction.A4(1), initialState)
                ), factoryParamsRecordings)
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 0)
            assertEquals(emptyList(), actionMapperRecordings)
            assertEquals(listOf<Pair<TestState, TestState>>(
                pairOf(initialState, TestState.S1)),
                stateMapperRecordings
            )

            //
            // Dispatch an action that is forwarded to child state machine
            //
            parent.dispatch(TestAction.A3)
            assertEquals(TestState.CounterState(2), awaitItem())
            // state mapper check
            assertEquals(listOf<Pair<TestState, TestState>>(
                pairOf(initialState, TestState.S1),
                pairOf(TestState.CounterState(1), TestState.S2)),
                stateMapperRecordings
            )
            // action mapper checks
            assertEquals(listOf<TestAction>(TestAction.A3), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 0)

            //
            // Dispatch another action that is forwarded to child state machine which
            // then also causes leaving the inState<CounterState> so sub child should be canceled
            //
            parent.dispatch(TestAction.A2)
            assertEquals(TestState.S3, awaitItem())
            // State mapper checks
            assertEquals(listOf<Pair<TestState, TestState>>(
                pairOf(initialState, TestState.S1),
                pairOf(TestState.CounterState(1), TestState.S2),
                pairOf(TestState.CounterState(2), TestState.S3)),
                stateMapperRecordings
            )
            // action mapper checks
            assertEquals(listOf<TestAction>(TestAction.A3, TestAction.A2), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked
            // child should be canceled because inState condition doesnt hold anymore
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 1)

        }
    }

}

private fun <A, B> pairOf(a: A, b: B): Pair<A, B> = a to b
