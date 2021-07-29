package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
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
            expectNoEvents()
        }
    }

    @Test
    fun `on entering the same state doesnt trigger onEnter again`() = suspendTest {
        var genericStateEntered = 0
        var a1Received = 0

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    OverrideState(TestState.GenericState("from initial", 0))
                }
            }

            inState<TestState.GenericState> {
                onEnter {
                    genericStateEntered++
                    OverrideState(TestState.GenericState("onEnter", 0))
                }

                on<TestAction.A1> { _, _ ->
                    a1Received++
                    OverrideState(TestState.GenericState("onA1", a1Received))
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, expectItem())
            assertEquals(TestState.GenericState("from initial", 0), expectItem())
            assertEquals(TestState.GenericState("onEnter", 0), expectItem())
            repeat(2) { index ->
                sm.dispatch(TestAction.A1) // Causes state transition to S1 again which is already current
                assertEquals(TestState.GenericState("onA1", index + 1), expectItem())
            }
        }
        assertEquals(1, genericStateEntered)
    }
}
