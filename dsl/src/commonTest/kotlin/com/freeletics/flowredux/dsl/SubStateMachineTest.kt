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
                    inS2OnA1Action = 0
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
    fun `sub statemachine factory is called every time perent state is entered`() =
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