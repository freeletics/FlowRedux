package com.freeletics.flowredux2.sideeffects

import app.cash.turbine.awaitItem
import app.cash.turbine.test
import com.freeletics.flowredux2.StateMachine
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnEnterTest {
    @Test
    fun onEnterBlockStopsWhenMovedToAnotherState() = runTest {
        val blockEntered = Channel<Boolean>(Channel.UNLIMITED)
        var cancellation: Throwable? = null

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    blockEntered.send(true)
                    try {
                        awaitCancellation()
                    } catch (t: Throwable) {
                        cancellation = t
                        throw t
                    }
                }

                on<TestAction.A2> {
                    override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertTrue(blockEntered.awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            assertIs<StateChangeCancellationException>(cancellation)
        }
    }

    @Test
    fun onEnteringTheSameStateDoesNotTriggerOnEnterAgain() = runTest {
        var genericStateEntered = 0
        var a1Received = 0

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    override { TestState.GenericState("from initial", 0) }
                }
            }

            inState<TestState.GenericState> {
                onEnter {
                    genericStateEntered++
                    override { TestState.GenericState("onEnter", 0) }
                }

                on<TestAction.A1> {
                    a1Received++
                    override { TestState.GenericState("onA1", a1Received) }
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
