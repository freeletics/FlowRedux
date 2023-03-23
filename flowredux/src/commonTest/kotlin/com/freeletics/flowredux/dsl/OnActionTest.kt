package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitComplete
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnActionTest {

    @Test
    fun actionBlockStopsWhenMovedToAnotherState() = runTest {
        val signal = Channel<Unit>()
        val blockEntered = Channel<Boolean>()

        var reached = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, state ->
                    blockEntered.send(true)
                    signal.awaitComplete()
                    // while we wait for S2 to be emitted which cancels the block the cancelattion might happen slightly afterwards
                    delay(100)
                    // this should never be reached because state transition did happen in the meantime,
                    // therefore this whole block must be canceled
                    reached = true
                    state.override { TestState.S1 }
                }

                on<TestAction.A2> { _, state ->
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertTrue(blockEntered.awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            signal.close()
            advanceUntilIdle()
            runCurrent()
        }

        assertFalse(reached)
    }

    @Test
    fun onActionGetsTriggeredAndMovesToNextState() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, state ->
                    state.override { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A2> { _, state ->
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
        }
    }
}
