package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class OnEnterTest {

    private val delay = 200L

    @Test
    fun `onEnter block stops when moved to another state`() = suspendTest {
        launch {
            var reached = false;
            var blockEntered = false
            val sm = StateMachine {
                inState<TestState.Initial> {
                    onEnter {
                        blockEntered = true
                        delay(delay)
                        // this should never be reached because state transition did happen in the meantime,
                        // therefore this whole block must be canceled
                        reached = true
                        OverrideState(TestState.S1)
                    }

                    on<TestAction.A2> { _, _ ->
                        OverrideState(TestState.S2)
                    }
                }
            }

            sm.state.test {
                assertEquals(TestState.Initial, expectItem())
                delay(delay / 2)
                assertTrue(blockEntered)
                dispatchAsync(sm, TestAction.A2)
                assertFalse(reached)
                assertEquals(TestState.S2, expectItem())
                delay(delay)
                expectComplete()
            }
        }
    }

    @Test
    fun `on entering the same state doesnt trigger onEnter again`() {
        suspendTest {
            var s1Entered = 0
            val sm = StateMachine {
                inState<TestState.Initial> {
                    onEnter { _ ->
                        OverrideState(TestState.S1)
                    }
                }

                inState<TestState.S1> {
                    onEnter { _ ->
                        s1Entered++
                        MutateState { this }
                    }
                    on<TestAction.A1> { _, _ -> OverrideState(TestState.S1) }
                }
            }

            launch {
                sm.state.test {
                    // switch to from Initial to S1 is immediate, before we start collecting
                    assertEquals(TestState.S1, expectItem())
                    repeat(2) {
                        dispatchAsync(sm, TestAction.A1) // Causes state transition to S1 again which is already current
                        expectNoEvents()
                        assertEquals(1, s1Entered)
                    }
                }
            }
        }
    }
}
