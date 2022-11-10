package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import com.freeletics.flowredux.suspendTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SubStateMachineTest {

    @Test
    fun `child statemachine emits initial state to parent state machine`() = suspendTest {
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
    fun `delegate to child sub statemachine while in state`() = suspendTest {
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
    fun `sub statemachine doesnt restart if state parent state is still the same`() =
        suspendTest {
            var factoryInvocations = 0
            var childEntersInitialState = 0

            val child = ChildStateMachine {
                inState<TestState.Initial> {
                    onEnterEffect {
                        childEntersInitialState++
                    }
                }
            }

            val sm = StateMachine(initialState = TestState.GenericState("generic", 0)) {
                inState<TestState.GenericState> {
                    onEnterStartStateMachine(
                        stateMachineFactory = {
                            factoryInvocations++
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
                assertEquals(1, factoryInvocations)
                assertEquals(1, childEntersInitialState)

                sm.dispatchAsync(TestAction.A1)
                assertEquals(TestState.GenericState("generic", 1), awaitItem())
                assertEquals(1, factoryInvocations)
                assertEquals(1, childEntersInitialState)

                sm.dispatchAsync(TestAction.A1)
                assertEquals(TestState.GenericState("generic", 2), awaitItem())
                assertEquals(1, factoryInvocations)
                assertEquals(1, childEntersInitialState)
            }
        }

    @Test
    fun `sub statemachine factory is called every time parent state is entered`() =
        suspendTest {
            var factoryInvocations = 0
            var childEntersInitialState = 0

            val child = ChildStateMachine {
                inState<TestState.Initial> {
                    onEnterEffect {
                        childEntersInitialState++
                    }
                }
            }

            val sm = StateMachine(initialState = TestState.S1) {
                inState<TestState.S1> {
                    onEnterStartStateMachine(
                        stateMachineFactory = {
                            factoryInvocations++
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
                assertEquals(1, factoryInvocations)
                assertEquals(1, childEntersInitialState)

                sm.dispatchAsync(TestAction.A1)

                assertEquals(TestState.S2, awaitItem())
                assertEquals(1, factoryInvocations)
                assertEquals(1, childEntersInitialState)

                sm.dispatchAsync(TestAction.A2)

                assertEquals(TestState.S1, awaitItem())
                delay(200)
                assertEquals(2, factoryInvocations)
                assertEquals(2, childEntersInitialState)

                sm.dispatchAsync(TestAction.A1)

                assertEquals(TestState.S2, awaitItem())
                assertEquals(2, factoryInvocations)
                assertEquals(2, childEntersInitialState)
            }
        }

    @Test
    fun `actions are only dispatched to sub statemachine while parent statemachine is in state`() =
        suspendTest {
            var childActionInvocations = 0
            var parentActionInvocations = 0
            val child = ChildStateMachine(initialState = TestState.S1) {
                inState<TestState.S1> {
                    on<TestAction.A1> { _, state ->
                        childActionInvocations++
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
                        parentActionInvocations++
                        state.noChange()
                    }
                }
            }

            sm.state.test {
                // initial state
                assertEquals(TestState.S1, awaitItem())

                // dispatch action to child statemachine
                sm.dispatch(TestAction.A1)
                delay(10) // give child a bit of time before continuing
                assertEquals(childActionInvocations, 1)

                // transition parent to other state
                sm.dispatch(TestAction.A2)
                assertEquals(TestState.S2, awaitItem())

                // dispatch A1 action which is part of child definition but should not be
                //  handled by child because parent not in state where delegation to child happens
                for (i in 1..3) {
                    sm.dispatch(TestAction.A1)
                    delay(10)
                    assertEquals(parentActionInvocations, i)
                    assertEquals(childActionInvocations, 1) // still 1, no change
                }
            }
        }

    @Test
    fun `actions are only dispatched to sub statemachine if they are mapped`() =
        suspendTest {
            var childActionInvocations = 0
            var parentActionInvocations = 0
            val child = ChildStateMachine(initialState = TestState.S1) {
                inState<TestState> {
                    on<TestAction> { _, state ->
                        childActionInvocations++
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
                        parentActionInvocations++
                        state.noChange()
                    }
                }
            }

            sm.state.test {
                // initial state
                assertEquals(TestState.S1, awaitItem())

                // dispatch mapped A1 action
                sm.dispatch(TestAction.A1)
                delay(10) // give child a bit of time before continuing
                assertEquals(childActionInvocations, 1)
                assertEquals(parentActionInvocations, 1)

                // dispatch mapped A2 action
                sm.dispatch(TestAction.A2)
                delay(10) // give child a bit of time before continuing
                assertEquals(childActionInvocations, 2)
                assertEquals(parentActionInvocations, 2)

                // dispatch unmapped A3 action
                sm.dispatch(TestAction.A3)
                delay(10) // give child a bit of time before continuing
                // assert that child state machine was not triggered after first two initial actions
                assertEquals(childActionInvocations, 2)
                assertEquals(parentActionInvocations, 3)

                // dispatch unmapped A4 action
                sm.dispatch(TestAction.A4(0))
                delay(10) // give child a bit of time before continuing
                // assert that child state machine was not triggered after first two initial actions
                assertEquals(childActionInvocations, 2)
                assertEquals(parentActionInvocations, 4)
            }
        }

    @Test
    fun `reentering state so that substatemachine triggers works with same child instate`() =
        suspendTest {
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
