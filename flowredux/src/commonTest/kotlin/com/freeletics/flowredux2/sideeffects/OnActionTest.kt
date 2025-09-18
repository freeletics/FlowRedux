package com.freeletics.flowredux2.sideeffects

import app.cash.turbine.Turbine
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.freeletics.flowredux2.ExecutionPolicy
import com.freeletics.flowredux2.stateMachine
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import com.freeletics.flowredux2.dispatchAsync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnActionTest {
    @Test
    fun actionBlockStopsWhenMovedToAnotherStateWithin10Milliseconds() = runTest {
        val blockEntered = Channel<Boolean>()
        var cancellation: Throwable? = null

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
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
            sm.dispatchAsync(TestAction.A1)
            assertTrue(blockEntered.awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            assertIs<StateChangeCancellationException>(cancellation)
        }
    }

    @Test
    fun onActionGetsTriggeredAndMovesToNextState() = runTest {
        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A2> {
                    override { TestState.S2 }
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

    @Test
    fun onActionOrdered() = runTest {
        turbineScope {
            val sm = stateMachine(TestState.GenericNullableState(null, null)) {
                inState<TestState.GenericNullableState> {
                    on<TestAction.A1>(executionPolicy = ExecutionPolicy.Ordered) {
                        mutate { copy(aString = "1") }
                    }

                    on<TestAction.A2>(executionPolicy = ExecutionPolicy.Ordered) {
                        mutate { copy(anInt = 2) }
                    }
                }
            }

            val scope = CoroutineScope(context = Dispatchers.Unconfined)
            val turbine = sm.state.testIn(scope)
            scope.launch {
                sm.dispatch(TestAction.A2)
                sm.dispatch(TestAction.A1)
            }

            assertEquals(TestState.GenericNullableState(null, null), turbine.awaitItem())
            assertEquals(TestState.GenericNullableState("1", null), turbine.awaitItem())
            assertEquals(TestState.GenericNullableState("1", 2), turbine.awaitItem())
        }
    }

    @Test
    fun onActionThrottled() = runTest {
        val timeSource = TestTimeSource()
        turbineScope {
            val sm = stateMachine(TestState.GenericNullableState(null, null)) {
                inState<TestState.GenericNullableState> {
                    on<TestAction.A1>(executionPolicy = ExecutionPolicy.Throttled(500.milliseconds, timeSource)) {
                        mutate { copy(aString = (aString ?: "") + "1") }
                    }

                    on<TestAction.A2>(executionPolicy = ExecutionPolicy.Throttled(1000.milliseconds, timeSource)) {
                        mutate { copy(anInt = (anInt ?: 0) + 1) }
                    }
                }
            }

            sm.state.test {
                assertEquals(TestState.GenericNullableState(null, null), awaitItem())

                // immediately handles first actions
                sm.dispatch(TestAction.A1)
                assertEquals(TestState.GenericNullableState("1", null), awaitItem())
                sm.dispatch(TestAction.A2)
                assertEquals(TestState.GenericNullableState("1", 1), awaitItem())

                // ignored actions until reaching time window
                sm.dispatch(TestAction.A1)
                sm.dispatch(TestAction.A2)
                ensureAllEventsConsumed()
                timeSource += 200.milliseconds
                sm.dispatch(TestAction.A1)
                sm.dispatch(TestAction.A2)
                ensureAllEventsConsumed()

                // handle A1 after reaching it's time window
                timeSource += 300.milliseconds
                sm.dispatch(TestAction.A1)
                assertEquals(TestState.GenericNullableState("11", 1), awaitItem())

                // ignored actions until reaching time window
                sm.dispatch(TestAction.A2)
                ensureAllEventsConsumed()
                timeSource += 300.milliseconds
                sm.dispatch(TestAction.A1)
                sm.dispatch(TestAction.A2)
                ensureAllEventsConsumed()

                // handle A1 and A2 after reaching both time windows
                timeSource += 200.milliseconds
                sm.dispatch(TestAction.A1)
                assertEquals(TestState.GenericNullableState("111", 1), awaitItem())
                sm.dispatch(TestAction.A2)
                assertEquals(TestState.GenericNullableState("111", 2), awaitItem())
            }
        }
    }

    @Test
    fun onActionThrottledWithSlowHandlers() = runTest {
        val timeSource = TestTimeSource()
        val startTime = timeSource.markNow()
        val receivedActionSignal = Turbine<Unit>()
        turbineScope {
            val sm = stateMachine(TestState.GenericNullableState(null, null)) {
                inState<TestState.GenericNullableState> {
                    on<TestAction.A1>(executionPolicy = ExecutionPolicy.Throttled(500.milliseconds, timeSource)) {
                        receivedActionSignal.add(Unit)
                        while (startTime.elapsedNow() < 600.milliseconds) {
                            delay(100)
                        }
                        mutate { copy(aString = (aString ?: "") + "1") }
                    }
                }
            }

            sm.state.test {
                assertEquals(TestState.GenericNullableState(null, null), awaitItem())

                // immediately handles first actions
                sm.dispatch(TestAction.A1)
                receivedActionSignal.awaitItem()

                timeSource += 500.milliseconds
                sm.dispatch(TestAction.A1)

                timeSource += 100.milliseconds
                assertEquals(TestState.GenericNullableState("1", null), awaitItem())
                ensureAllEventsConsumed()

                sm.dispatch(TestAction.A1)
                assertEquals(TestState.GenericNullableState("11", null), awaitItem())
            }
        }
    }
}
