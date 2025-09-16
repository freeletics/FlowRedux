package com.freeletics.flowredux2.sideeffects

import app.cash.turbine.Turbine
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import com.freeletics.flowredux2.FlowReduxBuilder
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.stateMachine
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import com.freeletics.flowredux2.dispatchAsync
import com.freeletics.flowredux2.initializeWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnEnterStartStateMachineTest {
    @Test
    fun childStateMachineEmitsInitialStateToParentStateMachine() = runTest {
        val child = childStateMachine(initialState = TestState.S3) { }
        val stateMachine = stateMachine {
            inState<TestState.Initial> {
                onEnterStartStateMachine({ child }) { override { it } }
            }
        }

        stateMachine.state.test {
            assertEquals(TestState.Initial, awaitItem()) // parent initial state
            assertEquals(TestState.S3, awaitItem()) // child initial state
        }
    }

    @Test
    fun delegateToChildSubStateMachineWhileInState() = runTest {
        var inS3onA1Action = 0
        var inS2OnA1Action = 0
        val receivedChildStateUpdates = mutableListOf<TestState>()
        val receivedChildStateUpdatesParentState = mutableListOf<TestState>()

        val child = childStateMachine(initialState = TestState.S3) {
            inState<TestState.S3> {
                on<TestAction.A1> {
                    inS3onA1Action++
                    override { TestState.S1 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A1> {
                    inS2OnA1Action++
                    override { TestState.S2 }
                }
            }
        }

        val parent = stateMachine {
            inState<TestState.Initial> {
                onEnterStartStateMachine(
                    stateMachineFactoryBuilder = { child },
                    actionMapper = { it },
                ) { subStateMachineState ->
                    receivedChildStateUpdates += subStateMachineState
                    receivedChildStateUpdatesParentState += snapshot
                    if (subStateMachineState == TestState.S3) {
                        noChange()
                    } else {
                        override { subStateMachineState }
                    }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> {
                    override { TestState.S2 }
                }
            }
        }

        parent.state.test {
            assertEquals(TestState.Initial, awaitItem())
            parent.dispatchAsync(TestAction.A1)

            assertEquals(TestState.S1, awaitItem())

            parent.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S2, awaitItem())

            parent.dispatch(TestAction.A1)

            assertEquals(1, inS3onA1Action)
            assertEquals(0, inS2OnA1Action)
            assertEquals(listOf(TestState.S3, TestState.S1), receivedChildStateUpdates)
            assertEquals(
                listOf<TestState>(TestState.Initial, TestState.Initial),
                receivedChildStateUpdatesParentState,
            )
        }
    }

    @Test
    fun subStateMachineDoesNotRestartIfStateParentStateIsStillTheSame() = runTest {
        val factoryInvocations = Channel<Unit>(Channel.UNLIMITED)
        val childEntersInitialState = Channel<Unit>(Channel.UNLIMITED)

        val child = childStateMachine {
            inState<TestState.Initial> {
                onEnterEffect {
                    childEntersInitialState.send(Unit)
                }
            }
        }

        val sm = stateMachine(initialState = TestState.GenericState("generic", 0)) {
            inState<TestState.GenericState> {
                onEnterStartStateMachine(
                    stateMachineFactoryBuilder = {
                        launch {
                            factoryInvocations.send(Unit)
                        }
                        child
                    },
                    actionMapper = { it },
                    handler = { noChange() },
                )

                on<TestAction.A1> {
                    override { copy(anInt = anInt + 1) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("generic", 0), awaitItem())
            assertEquals(Unit, factoryInvocations.awaitItem())
            assertEquals(Unit, childEntersInitialState.awaitItem())

            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.GenericState("generic", 1), awaitItem())
            assertTrue(factoryInvocations.isEmpty)
            assertTrue(childEntersInitialState.isEmpty)

            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.GenericState("generic", 2), awaitItem())
            assertTrue(factoryInvocations.isEmpty)
            assertTrue(childEntersInitialState.isEmpty)
        }
    }

    @Test
    fun subStateMachineFactoryIsCalledEveryTimeParentStateIsEntered() = runTest {
        val factoryInvocations = Channel<Unit>(Channel.UNLIMITED)
        val childEntersInitialState = Channel<Unit>(Channel.UNLIMITED)

        val child = childStateMachine {
            inState<TestState.Initial> {
                onEnterEffect {
                    childEntersInitialState.send(Unit)
                }
            }
        }

        val sm = stateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onEnterStartStateMachine(
                    stateMachineFactoryBuilder = {
                        launch {
                            factoryInvocations.send(Unit)
                        }
                        child
                    },
                    actionMapper = { it },
                    handler = { noChange() },
                )

                on<TestAction.A1> {
                    override { TestState.S2 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> {
                    override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.S1, awaitItem())
            assertEquals(Unit, factoryInvocations.awaitItem())
            assertEquals(Unit, childEntersInitialState.awaitItem())

            sm.dispatchAsync(TestAction.A1)

            assertEquals(TestState.S2, awaitItem())
            assertTrue(factoryInvocations.isEmpty)
            assertTrue(childEntersInitialState.isEmpty)

            sm.dispatchAsync(TestAction.A2)

            assertEquals(TestState.S1, awaitItem())
            assertEquals(Unit, factoryInvocations.awaitItem())
            assertEquals(Unit, childEntersInitialState.awaitItem())

            sm.dispatchAsync(TestAction.A1)

            assertEquals(TestState.S2, awaitItem())
            assertTrue(factoryInvocations.isEmpty)
            assertTrue(childEntersInitialState.isEmpty)
        }
    }

    @Test
    fun actionsAreOnlyDispatchedToSubStateMachineWhileParentStateMachineIsInState() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val child = childStateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A1> {
                    childActionInvocations.send(Unit)
                    noChange()
                }
            }
        }

        val sm = stateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onEnterStartStateMachine({ child }) { override { it } }
                on<TestAction.A2> { override { TestState.S2 } }
            }
            inState<TestState.S2> {
                on<TestAction.A1> {
                    parentActionInvocations.send(Unit)
                    noChange()
                }
            }
        }

        sm.state.test {
            // initial state
            assertEquals(TestState.S1, awaitItem())

            // dispatch action to child state machine
            sm.dispatch(TestAction.A1)
            assertEquals(Unit, childActionInvocations.awaitItem())

            // transition parent to other state
            sm.dispatch(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())

            // dispatch A1 action which is part of child definition but should not be
            //  handled by child because parent not in state where delegation to child happens
            repeat(3) {
                sm.dispatch(TestAction.A1)
                assertEquals(Unit, parentActionInvocations.awaitItem())
                assertTrue(childActionInvocations.isEmpty) // no further emissions
            }
        }
    }

    @Test
    fun actionsAreOnlyDispatchedToSubStateMachineIfTheyAreMapped() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val child = childStateMachine(initialState = TestState.S1) {
            inState<TestState> {
                on<TestAction> {
                    childActionInvocations.send(Unit)
                    noChange()
                }
            }
        }

        val sm = stateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onEnterStartStateMachine(
                    stateMachineFactoryBuilder = { child },
                    actionMapper = {
                        when (it) {
                            TestAction.A1 -> it
                            TestAction.A2 -> it
                            TestAction.A3 -> null
                            is TestAction.A4 -> null
                        }
                    },
                ) { override { it } }
                on<TestAction> {
                    parentActionInvocations.send(Unit)
                    noChange()
                }
            }
        }

        sm.state.test {
            // initial state
            assertEquals(TestState.S1, awaitItem())

            // dispatch mapped A1 action
            sm.dispatch(TestAction.A1)
            assertEquals(Unit, childActionInvocations.awaitItem())
            assertEquals(Unit, parentActionInvocations.awaitItem())

            // dispatch mapped A2 action
            sm.dispatch(TestAction.A2)
            assertEquals(Unit, childActionInvocations.awaitItem())
            assertEquals(Unit, parentActionInvocations.awaitItem())

            // dispatch unmapped A3 action
            sm.dispatch(TestAction.A3)
            // assert that child state machine was not triggered after first two initial actions
            assertTrue(childActionInvocations.isEmpty)
            assertEquals(Unit, parentActionInvocations.awaitItem())

            // dispatch unmapped A4 action
            sm.dispatch(TestAction.A4(0))
            // assert that child state machine was not triggered after first two initial actions
            assertTrue(childActionInvocations.isEmpty)
            assertEquals(Unit, parentActionInvocations.awaitItem())
        }
    }

    @Test
    fun reenteringStateSoThatSubStateMachineTriggersWorksWithSameChildInState() = runTest {
        val childOnEnterS2 = Turbine<Unit>()
        val childActionA2 = Turbine<Unit>()
        val parentS2 = Turbine<Unit>()
        val childFactory = Turbine<Unit>()

        val child = childStateMachine(initialState = TestState.S2) {
            inState<TestState.S2> {
                onEnter {
                    childOnEnterS2.add(Unit)
                    noChange()
                }
                on<TestAction.A2> {
                    childActionA2.add(Unit)
                    override { TestState.S1 }
                }
            }
        }

        val sm = stateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A1> { override { TestState.S2 } }
            }
            inState<TestState.S2> {
                onEnterEffect { parentS2.add(Unit) }
                onEnterStartStateMachine(
                    stateMachineFactoryBuilder = {
                        childFactory.add(Unit)
                        child
                    },
                    actionMapper = { it },
                    handler = { childState -> override { childState } },
                )
            }
        }

        sm.state.test {
            // initial state
            assertEquals(TestState.S1, awaitItem())

            for (i in 1..3) {
                // move to S2
                sm.dispatch(TestAction.A1)
                assertEquals(TestState.S2, awaitItem())
                assertEquals(Unit, parentS2.awaitItem())
                assertEquals(Unit, childFactory.awaitItem())
                assertEquals(Unit, childOnEnterS2.awaitItem())

                // dispatch action to child and move back to S1
                sm.dispatch(TestAction.A2)
                assertEquals(TestState.S1, awaitItem())
                assertEquals(Unit, childActionA2.awaitItem())
                childFactory.expectNoEvents()
            }
        }
    }

    private fun childStateMachine(
        initialState: TestState = TestState.Initial,
        builderBlock: FlowReduxBuilder<TestState, TestAction>.() -> Unit,
    ): FlowReduxStateMachineFactory<TestState, TestAction> {
        return object : FlowReduxStateMachineFactory<TestState, TestAction>() {
            init {
                initializeWith(reuseLastEmittedStateOnLaunch = false) { initialState }
                spec(builderBlock)
            }
        }
    }
}
