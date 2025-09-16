package com.freeletics.flowredux2.sideeffects

import app.cash.turbine.awaitItem
import app.cash.turbine.test
import com.freeletics.flowredux2.stateMachine
import com.freeletics.flowredux2.StateMachineFactory
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import com.freeletics.flowredux2.dispatchAsync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnActionStartStateMachineTest {
    @Test
    fun childStateMachineEmitsInitialStateToParentStateMachine() = runTest {
        var childStateChanged = 0
        val child = StateMachineFactory(initialState = TestState.S3)
        val parentStateMachine = stateMachine {
            inState<TestState.Initial> {
                onActionStartStateMachine<TestAction.A1, TestState>({ child }) { childState ->
                    childStateChanged++
                    override { childState }
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
    fun actionsAreForwardedToTheSubStateMachineAndSubStateIsPropagatedBackONLYWhileInState() = runTest {
        var childStateChanged = 0
        var childS3A2Handled = 0
        var childS1A2Handled = 0
        val recordedSubStates = mutableListOf<TestState>()

        val child = StateMachineFactory(initialState = TestState.S3) {
            inState<TestState.S3> {
                on<TestAction.A2> {
                    childS3A2Handled++
                    override {
                        TestState.S1
                    } // Doesn't really matter which state, parent ignores it anyway
                }
            }
            inState<TestState.S1> {
                on<TestAction.A2> {
                    childS1A2Handled++
                    override { TestState.S3 }
                }
            }
        }

        val parentStateMachine = stateMachine(initialState = TestState.CounterState(0)) {
            inState<TestState.CounterState> {
                onActionStartStateMachine<TestAction.A1, TestState>({ child }) { childState ->
                    childStateChanged++
                    recordedSubStates += childState
                    mutate { copy(counter = this.counter + 1) }
                }

                on<TestAction.A3> {
                    override { TestState.S3 }
                }
            }

            inState<TestState.S3> {
                on<TestAction.A2> {
                    override { TestState.S2 }
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
            assertEquals(TestState.S2, awaitItem())
            // verify child state machine had no interactions
            assertEquals(1, childS3A2Handled)
            assertEquals(1, childS1A2Handled)
            assertEquals(3, childStateChanged)
        }
    }

    @Test
    fun subStateMachineFactoryIsInvokedOnReEnterAndActionAndHandlerAreInvoked() = runTest {
        val actionMapperRecordings = mutableListOf<TestAction>()
        val factoryParamsRecordings = mutableListOf<Pair<TestAction, TestState>>()
        val handlerRecordings = mutableListOf<Pair<TestState, TestState>>()
        val initialState = TestState.CounterState(0)

        val child = StateMachineFactory(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A3> { override { TestState.S2 } }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { override { TestState.S3 } }
            }
        }
        val parent = stateMachine(initialState = initialState) {
            inState<TestState.S3> {
                on<TestAction.A1> { override { TestState.CounterState(10) } }
            }
            inState<TestState.CounterState> {
                onActionStartStateMachine<TestAction.A4, TestState, TestAction>(
                    stateMachineFactoryBuilder = { action ->
                        factoryParamsRecordings += pairOf(action, snapshot)
                        child
                    },
                    actionMapper = {
                        actionMapperRecordings += it
                        it
                    },
                    handler = { subState ->
                        handlerRecordings += pairOf(snapshot, subState)
                        if (subState is TestState.S3) {
                            override { TestState.S3 }
                        } else {
                            mutate {
                                copy(counter = this.counter + 1)
                            }
                        }
                    },
                )
            }
        }

        parent.state.test {
            //
            // Initial setup an initial state emission checks
            //
            assertEquals(TestState.CounterState(0), awaitItem()) // initial state
            assertEquals(emptyList(), factoryParamsRecordings) // factory should not have been invoked

            //
            // Dispatch action to start child state machine and check factory
            //
            parent.dispatch(TestAction.A4(1))
            assertEquals(TestState.CounterState(1), awaitItem()) // initial emission of child
            assertEquals(
                // factory should have been invoked
                listOf<Pair<TestAction, TestState>>(
                    pairOf(TestAction.A4(1), initialState),
                ),
                factoryParamsRecordings,
            )
            assertEquals(emptyList(), actionMapperRecordings)
            assertEquals(
                listOf<Pair<TestState, TestState>>(
                    pairOf(initialState, TestState.S1),
                ),
                handlerRecordings,
            )

            //
            // Dispatch an action that is forwarded to child state machine
            //
            parent.dispatch(TestAction.A3)
            assertEquals(TestState.CounterState(2), awaitItem())
            // state mapper check
            assertEquals(
                listOf<Pair<TestState, TestState>>(
                    pairOf(initialState, TestState.S1),
                    pairOf(TestState.CounterState(1), TestState.S2),
                ),
                handlerRecordings,
            )
            // action mapper checks
            assertEquals(listOf<TestAction>(TestAction.A3), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked

            //
            // Dispatch another action that is forwarded to child state machine which
            // then also causes leaving the inState<CounterState> so sub child should be canceled
            //
            parent.dispatch(TestAction.A2)
            assertEquals(TestState.S3, awaitItem())
            // State mapper checks
            assertEquals(
                listOf<Pair<TestState, TestState>>(
                    pairOf(initialState, TestState.S1),
                    pairOf(TestState.CounterState(1), TestState.S2),
                    pairOf(TestState.CounterState(2), TestState.S3),
                ),
                handlerRecordings,
            )
            // action mapper checks
            assertEquals(listOf(TestAction.A3, TestAction.A2), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked

            //
            // Clear up stuff for validation of next re-entrance and child state machine starts
            //
            factoryParamsRecordings.clear()
            actionMapperRecordings.clear()
            handlerRecordings.clear()

            //
            // Re-enter the state again
            //
            parent.dispatch(TestAction.A1)
            assertEquals(TestState.CounterState(10), awaitItem())
            // check for actions (should not be dispatched to child)
            assertEquals(emptyList(), actionMapperRecordings)
            // factory checks
            assertEquals(emptyList(), factoryParamsRecordings) // factory not invoked

            //
            // Dispatch action to start child state machine
            //
            parent.dispatch(TestAction.A4(2))
            assertEquals(TestState.CounterState(11), awaitItem()) // initial emission of child
            // check factory
            assertEquals(
                // factory should have been invoked
                listOf<Pair<TestAction, TestState>>(
                    pairOf(TestAction.A4(2), TestState.CounterState(10)),
                ),
                factoryParamsRecordings,
            )
            // check action mapper (not changed since last check)
            assertEquals(emptyList(), actionMapperRecordings)
            // check state mapper
            assertEquals(
                listOf<Pair<TestState, TestState>>(
                    pairOf(TestState.CounterState(10), TestState.S1),
                ),
                handlerRecordings,
            )

            //
            // Dispatch an action that is forwarded to child state machine
            //
            parent.dispatch(TestAction.A3)
            assertEquals(TestState.CounterState(12), awaitItem())
            // state mapper check
            assertEquals(
                listOf<Pair<TestState, TestState>>(
                    pairOf(TestState.CounterState(10), TestState.S1),
                    pairOf(TestState.CounterState(11), TestState.S2),
                ),
                handlerRecordings,
            )
            // action mapper checks
            assertEquals(listOf<TestAction>(TestAction.A3), actionMapperRecordings)
            // factory checks
            assertEquals(1, factoryParamsRecordings.size) // factory not invoked
        }
    }

    @Test
    fun actionsAreOnlyDispatchedToSubStatemachineIfTheyAreNotMappedToNull() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)

        val child = StateMachineFactory(initialState = TestState.S1) {
            inState<TestState> {
                on<TestAction> {
                    childActionInvocations.send(Unit)
                    noChange()
                }
            }
        }

        val parent = stateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onActionStartStateMachine<TestAction.A4, TestState, TestAction>(
                    stateMachineFactoryBuilder = { child },
                    actionMapper = {
                        when (it) {
                            TestAction.A1 -> it
                            TestAction.A2 -> null
                            TestAction.A3 -> null
                            is TestAction.A4 -> null
                        }
                    },
                ) { childState ->
                    override { childState }
                }

                on<TestAction> {
                    parentActionInvocations.send(Unit)
                    noChange()
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

    private fun <A, B> pairOf(a: A, b: B): Pair<A, B> = a to b
}
