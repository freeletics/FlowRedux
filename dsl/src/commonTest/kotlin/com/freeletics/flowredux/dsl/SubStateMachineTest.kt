package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import com.freeletics.flowredux.dsl.internal.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class SubStateMachineTest {

    @Test
    fun `child statemachine emits initial state to parent state machine`() = suspendTest {
        val child = ChildStateMachine(initialState = TestState.S3) { }
        val parentStateMachine = StateMachine {
            inState<TestState.Initial> {
                stateMachine(child)
            }
        }

        parentStateMachine.state.test {
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
                on<TestAction.A1> { _, _ ->
                    inS3onA1Action++
                    OverrideState(TestState.S1)
                }
            }

            inState<TestState.S2> {
                on<TestAction.A1> { _, _ ->
                    inS2OnA1Action++
                    OverrideState(TestState.S2)
                }
            }
        }

        val parent = StateMachine {

            inState<TestState.Initial> {
                stateMachine(
                    stateMachine = child,
                    actionMapper = { it }
                ) { parentState, subStateMachineState ->
                    receivedChildStateUpdates += subStateMachineState
                    receivedChildStateUpdatesParentState += parentState
                    if (subStateMachineState == TestState.S3) {
                        NoStateChange
                    } else {
                        OverrideState(subStateMachineState)
                    }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> { _, _ ->
                    OverrideState(TestState.S2)
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
                    stateMachine(
                        stateMachineFactory = {
                            factoryInvocations++
                            child
                        },
                        actionMapper = { it },
                        stateMapper = { _, _ -> NoStateChange }
                    )

                    on<TestAction.A1> { _, snapshot ->
                        OverrideState(snapshot.copy(anInt = snapshot.anInt + 1))
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
                    stateMachine(
                        stateMachineFactory = {
                            factoryInvocations++
                            child
                        },
                        actionMapper = { it },
                        stateMapper = { _, _ -> NoStateChange }
                    )

                    on<TestAction.A1> { _, _ ->
                        OverrideState(TestState.S2)
                    }
                }

                inState<TestState.S2> {
                    on<TestAction.A2> { _, _ ->
                        OverrideState(TestState.S1)
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
                    on<TestAction.A1> { _, _ ->
                        childActionInvocations++
                        NoStateChange
                    }
                }
            }

            val sm = StateMachine(initialState = TestState.S1) {
                inState<TestState.S1> {
                    stateMachine(child)
                    on<TestAction.A2> { _, _ -> OverrideState(TestState.S2) }
                }
                inState<TestState.S2> {
                    on<TestAction.A1> { _, _ ->
                        parentActionInvocations++
                        NoStateChange
                    }
                }
            }

            sm.state.test {
                // initial state
                assertEquals(TestState.S1, awaitItem())

                // dispatch action to child statemachine
                sm.dispatch(TestAction.A1)
                delay(10) // give child a bit of time before continueing
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
                        NoStateChange
                    }
                    on<TestAction.A2> { _, _ ->
                        childActionA2++
                        OverrideState(TestState.S1)
                    }
                }
            }

            val sm = StateMachine(initialState = TestState.S1) {
                inState<TestState.S1> {
                    on<TestAction.A1> { _, _ -> OverrideState(TestState.S2) }
                }
                inState<TestState.S2> {
                    onEnterEffect { parentS2++ }
                    stateMachine(
                        stateMachineFactory = { childFactory++; child },
                        actionMapper = { it },
                        stateMapper = { _, childState -> OverrideState(childState) }
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
    return object : FlowReduxStateMachine<TestState, TestAction>(
        initialState,
        CommandLineLogger
    ) {

        init {
            spec(builderBlock)
        }
    }
}