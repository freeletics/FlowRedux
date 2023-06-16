package com.freeletics.flowredux.sideeffects

import app.cash.turbine.awaitItem
import app.cash.turbine.test
import com.freeletics.flowredux.StateMachine
import com.freeletics.flowredux.TestAction
import com.freeletics.flowredux.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnActionTest {

    @Test
    fun actionBlockStopsWhenMovedToAnotherStateWithin10Milliseconds() = runTest {
        val blockEntered = Channel<Boolean>()
        var cancellation: Throwable? = null

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, _ ->
                    blockEntered.send(true)
                    try {
                        awaitCancellation()
                    } catch (t: Throwable) {
                        cancellation = t
                        throw t
                    }
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
            assertIs<StateChangeCancellationException>(cancellation)
        }
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
