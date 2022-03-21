package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import com.freeletics.flowredux.suspendTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class OnEnterEffectTest {

    private val delay = 200L

    @Test
    fun `onEnter effect block stops when moved to another state`() = suspendTest {
        var reached = false;
        var blockEntered = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnterEffect {
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
            assertEquals(TestState.Initial, awaitItem())
            delay(delay / 2)
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            delay(delay)
            expectNoEvents()
        }

        assertTrue(blockEntered)
        assertFalse(reached)
    }

    @Test
    fun `on entering the same state doesnt trigger onEnterEffect again`() = suspendTest {
        var genericStateEffectEntered = 0
        var a1Received = 0

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    OverrideState(TestState.GenericState("from initial", 0))
                }
            }

            inState<TestState.GenericState> {
                onEnterEffect {
                    genericStateEffectEntered++
                }

                on<TestAction.A1> { _, _ ->
                    a1Received++
                    OverrideState(TestState.GenericState("onA1", a1Received))
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.GenericState("from initial", 0), awaitItem())
            repeat(2) { index ->
                sm.dispatch(TestAction.A1) // Causes state transition to S1 again which is already current
                assertEquals(TestState.GenericState("onA1", index + 1), awaitItem())
            }
        }
        assertEquals(1, genericStateEffectEntered)
    }
}
