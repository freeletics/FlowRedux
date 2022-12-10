package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitItem
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StartStateMachineOnActionInStateTest {

    @Test
    fun `child state machine emits initial state to parent state machine`() = runTest {
        var childStateChanged = 0
        val child = StateMachine(initialState = TestState.S3)
        val parentStateMachine = StateMachine {
            inState<TestState.Initial> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { inputState, childState ->
                    childStateChanged++
                    inputState.override { childState }
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
    fun `child state machine stops after leaving While In State`() = runTest {
        var childStateChanged = 0
        val child = StateMachine(initialState = TestState.S3)
        val parentStateMachine = StateMachine {
            inState<TestState.Initial> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { inputState, _ ->
                    childStateChanged++
                    inputState.override { TestState.S1 }
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
    fun `actions are forwarded to the sub state machine and sub state is propagated back ONLY while in state`() = runTest {
        var childStateChanged = 0
        var childS3A2Handled = 0
        var childS1A2Handled = 0
        val recordedSubStates = mutableListOf<TestState>()

        val child = StateMachine(initialState = TestState.S3) {
            inState<TestState.S3> {
                on<TestAction.A2> { _, state ->
                    childS3A2Handled++
                    state.override { TestState.S1 } // Doesn't really matter which state, parent ignores it anyway
                }
            }
            inState<TestState.S1> {
                on<TestAction.A2> { _, state ->
                    childS1A2Handled++
                    state.override { TestState.S3 }
                }
            }
        }

        val parentStateMachine = StateMachine(initialState = TestState.CounterState(0)) {
            inState<TestState.CounterState> {
                onActionStartStateMachine<TestAction.A1, TestState>(child) { inputState, childState ->
                    childStateChanged++
                    recordedSubStates += childState
                    inputState.mutate { copy(counter = this.counter + 1) }
                }

                on<TestAction.A3> { _, state ->
                    state.override { TestState.S3 }
                }
            }
        }

        parentStateMachine.state.test {
            assertEquals(TestState.CounterState(0), awaitItem()) // parent initial state
            parentStateMachine.dispatch(TestAction.A1) // starts child
            assertEquals(TestState.CounterState(1), awaitItem()) // initial state of sub state machine caused this change
            assertEquals(1, childStateChanged)
            assertEquals(listOf<TestState>(TestState.S3), recordedSubStates)

            parentStateMachine.dispatch(TestAction.A2) // dispatch Action to child state machine
            assertEquals(TestState.CounterState(2), awaitItem()) // state change because of A2
            assertEquals(1, childS3A2Handled)
            assertEquals(0, childS1A2Handled)
            assertEquals(2, childStateChanged)
            assertEquals(listOf(TestState.S3, TestState.S1), recordedSubStates)


            parentStateMachine.dispatch(TestAction.A2) // dispatch Action to child state machine
            assertEquals(TestState.CounterState(3), awaitItem()) // state change because of A2
            assertEquals(1, childS3A2Handled)
            assertEquals(1, childS1A2Handled)
            assertEquals(3, childStateChanged)
            assertEquals(listOf(TestState.S3, TestState.S1, TestState.S3), recordedSubStates)


            parentStateMachine.dispatch(TestAction.A3) // dispatch Action to parent state machine, causes state change
            assertEquals(TestState.S3, awaitItem())

            // Child state machine should have stopped because we are in S3 state
            parentStateMachine.dispatchAsync(TestAction.A2) // should not be handled by child state machine
            delay(50)
            // verify child state machine had no interactions
            assertEquals(1, childS3A2Handled)
            assertEquals(1, childS1A2Handled)
            assertEquals(3, childStateChanged)
        }

    }

    @Test
    fun `sub state machine factory is invoked on re-enter and action and state mapper are invoked`() = runTest {
        val actionMapperRecordings = mutableListOf<TestAction>()
        val factoryParamsRecordings = mutableListOf<Pair<TestAction, TestState>>()
        val stateMapperRecordings = mutableListOf<Pair<TestState, TestState>>()
        val initialState = TestState.CounterState(0)

        val child = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A3> { _, state -> state.override { TestState.S2 } }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { _, state -> state.override { TestState.S3 } }
            }
        }
        val parent = StateMachine(initialState = initialState) {
            inState<TestState.S3> {
                on<TestAction.A1> { _, state -> state.override { TestState.CounterState(10) } }
            }
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
                        stateMapperRecordings += pairOf(inputState.snapshot, subState)
                        if (subState is TestState.S3)
                            inputState.override { TestState.S3 }
                        else
                            inputState.mutate {
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
            assertEquals(listOf(TestAction.A3, TestAction.A2), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked
            // child should be canceled because inState condition does not hold anymore
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 1)

            //
            // Clear up stuff for validation of next re-entrance and child state machine starts
            //
            factoryParamsRecordings.clear()
            actionMapperRecordings.clear()
            stateMapperRecordings.clear()

            //
            // Re-enter the state again
            //
            parent.dispatch(TestAction.A1)
            assertEquals(TestState.CounterState(10), awaitItem())
            // check for actions (should not be dispatched to child)
            assertEquals(emptyList(), actionMapperRecordings)
            // factory checks
            assertEquals(emptyList(), factoryParamsRecordings) // factory not invoked
            // child should not have changed since before
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 1)

            //
            // Dispatch action to start child state machine
            //
            parent.dispatch(TestAction.A4(2))
            assertEquals(TestState.CounterState(11), awaitItem()) // initial emission of child
            // check factory
            assertEquals(  // factory should have been invoked
                listOf<Pair<TestAction, TestState>>(
                    pairOf(TestAction.A4(2), TestState.CounterState(10))
                ), factoryParamsRecordings)
            assertEquals(child.stateFlowStarted, 2)
            assertEquals(child.stateFlowCompleted, 1)
            // check action mapper (not changed since last check)
            assertEquals(emptyList(), actionMapperRecordings)
            // check state mapper
            assertEquals(listOf<Pair<TestState, TestState>>(
                pairOf(TestState.CounterState(10), TestState.S1)),
                stateMapperRecordings
            )

            //
            // Dispatch an action that is forwarded to child state machine
            //
            parent.dispatch(TestAction.A3)
            assertEquals(TestState.CounterState(12), awaitItem())
            // state mapper check
            assertEquals(listOf<Pair<TestState, TestState>>(
                pairOf(TestState.CounterState(10), TestState.S1),
                pairOf(TestState.CounterState(11), TestState.S2)),
                stateMapperRecordings
            )
            // action mapper checks
            assertEquals(listOf<TestAction>(TestAction.A3), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked
            assertEquals(child.stateFlowStarted, 2)
            assertEquals(child.stateFlowCompleted, 1)
        }
    }

    @Test
    fun `actions are only dispatched to sub statemachine if they are not mapped to null`() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)

        val child = StateMachine(initialState = TestState.S1) {
            inState<TestState> {
                on<TestAction> { _, state ->
                    childActionInvocations.send(Unit)
                    state.noChange()
                }
            }
        }

        val parent = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onActionStartStateMachine<TestAction.A4, TestState, TestAction>(
                    stateMachineFactory = { _, _ ->
                        child
                    },
                    actionMapper = {
                        when (it) {
                            TestAction.A1 -> it
                            TestAction.A2 -> null
                            TestAction.A3 -> null
                            is TestAction.A4 -> null
                        }
                    }
                ) { inputState, childState ->
                    inputState.override { childState }
                }

                on<TestAction> { _, state ->
                    parentActionInvocations.send(Unit)
                    state.noChange()
                }
            }
        }

        parent.state.test {
            // initial state
            assertEquals(TestState.S1, awaitItem())

            //
            // dispatch action to start child state machine and check factory
            //
            parent.dispatch(TestAction.A4(1))
            assertEquals(child.stateFlowStarted, 1)
            assertEquals(child.stateFlowCompleted, 0)

            // dispatch mapped A1 action
            parent.dispatch(TestAction.A1)
            assertEquals(Unit, childActionInvocations.awaitItem())
            assertEquals(Unit, parentActionInvocations.awaitItem())
            assertEquals(Unit, parentActionInvocations.awaitItem())

            // dispatch unmapped A2 action
            parent.dispatch(TestAction.A2)
            assertEquals(Unit, parentActionInvocations.awaitItem())
            assertTrue(childActionInvocations.isEmpty)

            // dispatch unmapped A3 action
            parent.dispatch(TestAction.A3)
            // assert that child state machine was not triggered after first two initial actions
            assertEquals(Unit, parentActionInvocations.awaitItem())
            assertTrue(childActionInvocations.isEmpty)
        }
    }
}

private fun <A, B> pairOf(a: A, b: B): Pair<A, B> = a to b
