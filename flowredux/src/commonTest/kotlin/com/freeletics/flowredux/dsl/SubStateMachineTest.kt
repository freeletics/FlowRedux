package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitItem
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SubStateMachineTest {

    @Test
    fun `child state machine emits initial state to parent state machine`() = runTest {
        val child = ChildStateMachine(initialState = TestState.S3) { }
        val stateMachine = StateMachine {
            inState<TestState.Initial> {
                onEnterStartStateMachine(child)
            }
        }

        stateMachine.state.test {
            assertEquals(TestState.Initial, awaitItem()) // parent initial state
            assertEquals(TestState.S3, awaitItem()) // child initial state
        }
    }

    @Test
    fun `delegate to child sub state machine while in state`() = runTest {
        var inS3onA1Action = 0
        var inS2OnA1Action = 0
        val receivedChildStateUpdates = mutableListOf<TestState>()
        val receivedChildStateUpdatesParentState = mutableListOf<TestState>()

        val child = ChildStateMachine(initialState = TestState.S3) {
            inState<TestState.S3> {
                on<TestAction.A1> { _, state ->
                    inS3onA1Action++
                    state.override { TestState.S1 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A1> { _, state ->
                    inS2OnA1Action++
                    state.override { TestState.S2 }
                }
            }
        }

        val parent = StateMachine {
            inState<TestState.Initial> {
                onEnterStartStateMachine(
                    stateMachine = child,
                    actionMapper = { it }
                ) { state, subStateMachineState ->
                    receivedChildStateUpdates += subStateMachineState
                    receivedChildStateUpdatesParentState += state.snapshot
                    if (subStateMachineState == TestState.S3) {
                        state.noChange()
                    } else {
                        state.override { subStateMachineState }
                    }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> { _, state ->
                    state.override { TestState.S2 }
                }
            }
        }

        parent.state.test {
            assertEquals(TestState.Initial, awaitItem())
            parent.dispatchAsync(TestAction.A1)

            assertEquals(TestState.S1, awaitItem())

            parent.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S2, awaitItem())

            assertEquals(1, inS3onA1Action)
            assertEquals(0, inS2OnA1Action)
            assertEquals(listOf(TestState.S3, TestState.S1), receivedChildStateUpdates)
            assertEquals(
                listOf<TestState>(TestState.Initial, TestState.Initial),
                receivedChildStateUpdatesParentState
            )
        }
    }

    @Test
    fun `sub state machine does not restart if state parent state is still the same`() = runTest {
        val factoryInvocations = Channel<Unit>(Channel.UNLIMITED)
        val childEntersInitialState = Channel<Unit>(Channel.UNLIMITED)

        val child = ChildStateMachine {
            inState<TestState.Initial> {
                onEnterEffect {
                    childEntersInitialState.send(Unit)
                }
            }
        }

        val sm = StateMachine(initialState = TestState.GenericState("generic", 0)) {
            inState<TestState.GenericState> {
                onEnterStartStateMachine(
                    stateMachineFactory = {
                        check(factoryInvocations.trySendBlocking(Unit).isSuccess)
                        child
                    },
                    actionMapper = { it },
                    stateMapper = { state, _ -> state.noChange() }
                )

                on<TestAction.A1> { _, state ->
                    state.override { state.snapshot.copy(anInt = state.snapshot.anInt + 1) }
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
    fun `sub state machine factory is called every time parent state is entered`() = runTest {
        val factoryInvocations = Channel<Unit>(Channel.UNLIMITED)
        val childEntersInitialState = Channel<Unit>(Channel.UNLIMITED)

            val child = ChildStateMachine {
                inState<TestState.Initial> {
                    onEnterEffect {
                        childEntersInitialState.send(Unit)
                    }
                }
            }

            val sm = StateMachine(initialState = TestState.S1) {
                inState<TestState.S1> {
                    onEnterStartStateMachine(
                        stateMachineFactory = {
                            check(factoryInvocations.trySendBlocking(Unit).isSuccess)
                            child
                        },
                        actionMapper = { it },
                        stateMapper = { state, _ -> state.noChange() }
                    )

                    on<TestAction.A1> { _, state ->
                        state.override { TestState.S2 }
                    }
                }

                inState<TestState.S2> {
                    on<TestAction.A2> { _, state ->
                        state.override { TestState.S1 }
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
    fun `actions are only dispatched to sub state machine while parent state machine is in state`() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val child = ChildStateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A1> { _, state ->
                    childActionInvocations.send(Unit)
                    state.noChange()
                }
            }
        }

        val sm = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onEnterStartStateMachine(child)
                on<TestAction.A2> { _, state -> state.override { TestState.S2 } }
            }
            inState<TestState.S2> {
                on<TestAction.A1> { _, state ->
                    parentActionInvocations.send(Unit)
                    state.noChange()
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
            for (i in 1..3) {
                sm.dispatch(TestAction.A1)
                assertEquals(Unit, parentActionInvocations.awaitItem())
                assertEquals(Unit, childActionInvocations.awaitItem()) // still 1, no change
            }
        }
    }

    @Test
    fun `actions are only dispatched to sub state machine if they are mapped`() = runTest {
        val childActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val parentActionInvocations = Channel<Unit>(Channel.UNLIMITED)
        val child = ChildStateMachine(initialState = TestState.S1) {
            inState<TestState> {
                on<TestAction> { _, state ->
                    childActionInvocations.send(Unit)
                    state.noChange()
                }
            }
        }

        val sm = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                onEnterStartStateMachine(
                    stateMachine = child,
                    actionMapper = {
                        when (it) {
                            TestAction.A1 -> it
                            TestAction.A2 -> it
                            TestAction.A3 -> null
                            is TestAction.A4 -> null
                        }
                    }
                )
                on<TestAction> { _, state ->
                    parentActionInvocations.send(Unit)
                    state.noChange()
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
    fun `reentering state so that sub state machine triggers works with same child instate`() = runTest {
        var childOnEnterS2 = 0
        var childActionA2 = 0
        var parentS2 = 0
        var childFactory = 0

        val child = ChildStateMachine(initialState = TestState.S2) {
            inState<TestState.S2> {
                onEnter {
                    childOnEnterS2++
                    it.noChange()
                }
                on<TestAction.A2> { _, state ->
                    childActionA2++
                    state.override { TestState.S1 }
                }
            }
        }

        val sm = StateMachine(initialState = TestState.S1) {
            inState<TestState.S1> {
                on<TestAction.A1> { _, state -> state.override { TestState.S2 } }
            }
            inState<TestState.S2> {
                onEnterEffect { parentS2++ }
                onEnterStartStateMachine(
                    stateMachineFactory = { childFactory++; child },
                    actionMapper = { it },
                    stateMapper = { state, childState -> state.override { childState } }
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
                assertEquals(i, parentS2)
                assertEquals(i, childOnEnterS2)
                assertEquals(i, childFactory)

                // dispatch action to child and move back to S1
                sm.dispatch(TestAction.A2)
                assertEquals(TestState.S1, awaitItem())
                assertEquals(i, childActionA2)
                assertEquals(i, childOnEnterS2)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
private fun ChildStateMachine(
    initialState: TestState = TestState.Initial,
    builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): FlowReduxStateMachine<TestState, TestAction> {
    return object : FlowReduxStateMachine<TestState, TestAction>(initialState) {

        init {
            spec(builderBlock)
        }
    }
}
