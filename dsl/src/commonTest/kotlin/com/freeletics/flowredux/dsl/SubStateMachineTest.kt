package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
        }

        parent.state.test {
            assertEquals(TestState.Initial, awaitItem())

            parent.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())

            assertEquals(1, inS3onA1Action)
            assertEquals(0, inS2OnA1Action)
            assertEquals(listOf(TestState.S3, TestState.S1), receivedChildStateUpdates)
            assertEquals(
                listOf<TestState>(TestState.Initial, TestState.Initial),
                receivedChildStateUpdatesParentState
            )
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