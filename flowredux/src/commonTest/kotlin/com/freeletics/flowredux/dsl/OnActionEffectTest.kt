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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OnActionEffectTest {

    private val delay = 200L

    @Test
    fun `action effect block stops when moved to another state`() = suspendTest {
        var reached = false;
        var reachedBefore = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
                    reachedBefore = true
                    delay(delay)
                    // this should never be reached because state transition did happen in the meantime,
                    // therefore this whole block must be canceled
                    reached = true
                }

                on<TestAction.A2> { _, state ->
                    state.override { TestState.S2 }
                }
            }

        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            delay(delay / 2)
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            delay(delay)
            expectNoEvents()
        }

        assertTrue(reachedBefore)
        assertFalse(reached)
    }

    @Test
    fun `on action effect is triggered`() = suspendTest {
        var effectTriggered = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
                    effectTriggered = true
                }

                on<TestAction.A1> { _, state ->
                    delay(10) // give onActionEffect a bit of time before changing state
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertFalse(effectTriggered)
            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S2, awaitItem())
            assertTrue(effectTriggered)
        }
    }
}
