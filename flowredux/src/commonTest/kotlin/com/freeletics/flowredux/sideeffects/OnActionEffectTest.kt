package com.freeletics.flowredux.sideeffects

import app.cash.turbine.awaitComplete
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
internal class OnActionEffectTest {

    @Test
    fun actionEffectBlockStopsWhenMovedToAnotherState() = runTest {
        val blockEntered = Channel<Boolean>(Channel.UNLIMITED)
        var cancellation: Throwable? = null

        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
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
    fun onActionEffectIsTriggered() = runTest {
        val signal = Channel<Unit>()
        val triggered = Channel<Boolean>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
                    triggered.send(true)
                }

                on<TestAction.A1> { _, state ->
                    signal.awaitComplete()
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertTrue(triggered.awaitItem())
            signal.close()
            assertEquals(TestState.S2, awaitItem())
        }
    }
}
