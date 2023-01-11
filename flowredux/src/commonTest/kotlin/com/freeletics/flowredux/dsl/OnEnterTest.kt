package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitComplete
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OnEnterTest {

    @Test
    fun `onEnter block stops when moved to another state`() = runTest {
        val signal = Channel<Unit>()
        val blockEntered = Channel<Boolean>(Channel.UNLIMITED)

        var reached = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    blockEntered.send(true)
                    signal.awaitComplete()
                    // this should never be reached because state transition did happen in the meantime,
                    // therefore this whole block must be canceled
                    reached = true
                    it.override { TestState.S1 }
                }

                on<TestAction.A2> { _, state ->
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertTrue(blockEntered.awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            signal.close()
        }

        assertFalse(reached)
    }

    @Test
    fun `on entering the same state does not trigger onEnter again`() = runTest {
        var genericStateEntered = 0
        var a1Received = 0

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    it.override { TestState.GenericState("from initial", 0) }
                }
            }

            inState<TestState.GenericState> {
                onEnter {
                    genericStateEntered++
                    it.override { TestState.GenericState("onEnter", 0) }
                }

                on<TestAction.A1> { _, state ->
                    a1Received++
                    state.override { TestState.GenericState("onA1", a1Received) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.GenericState("from initial", 0), awaitItem())
            assertEquals(TestState.GenericState("onEnter", 0), awaitItem())
            repeat(2) { index ->
                sm.dispatch(TestAction.A1) // Causes state transition to S1 again which is already current
                assertEquals(TestState.GenericState("onA1", index + 1), awaitItem())
            }
        }
        assertEquals(1, genericStateEntered)
    }
}
