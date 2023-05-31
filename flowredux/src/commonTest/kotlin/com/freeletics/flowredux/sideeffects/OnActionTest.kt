package com.freeletics.flowredux.sideeffects

import app.cash.turbine.awaitComplete
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import com.freeletics.flowredux.StateMachine
import com.freeletics.flowredux.TestAction
import com.freeletics.flowredux.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnActionTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun actionBlockStopsWhenMovedToAnotherStateWithin10Milliseconds() = runTest {
        val signal = Channel<Unit>()
        val blockEntered = Channel<Boolean>()

        var reached = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, state ->
                    blockEntered.send(true)
                    signal.awaitComplete()
                    // while we wait for S2 to be emitted which cancels this block
                    // the cancellation of this block might happen a few milliseconds later.
                    // Therefore we add a tiny delay and use a "real" dispatcher where delay would wait
                    // for real (in contrast to TestDipsatcher used with runTest {...})
                    // to avoid flakiness.
                    withContext(Dispatchers.Default) {
                        val timeElapsed = measureTime {
                            // 10 ms should be enough to make sure that the cancellation happened in the meantime
                            // because of state transition to TestState.S2 in on<TestAction.A2>.
                            delay(10)
                        }
                        assertTrue(timeElapsed.toDouble(DurationUnit.MILLISECONDS) < 10, "Time Elapsed: $timeElapsed but expected to be < 10")
                    }
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
