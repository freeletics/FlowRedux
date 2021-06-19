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
class OnActionTest {

    private val delay = 200L

    @Test
    fun `action block stops when moved to another state`() = suspendTest {
        launch {
            var reached = false;
            var reachedBefore = false
            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _ ->
                        reachedBefore = true
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
                dispatchAsync(sm, TestAction.A1)
                delay(delay/2)
                dispatchAsync(sm, TestAction.A2)
                assertTrue(reachedBefore)
                assertFalse(reached)
                assertEquals(TestState.S2, expectItem())
                delay(delay)
                expectComplete()
            }
        }
    }

    @Test
    fun `on action gets triggered and moves to next state`() = suspendTest {

        launch {
            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _ ->
                        OverrideState(TestState.S1)
                    }
                }

                inState<TestState.S1> {
                    on<TestAction.A2> { _, _ ->
                        OverrideState(TestState.S2)
                    }

                }

            }
            sm.state.test {
                assertEquals(TestState.Initial, expectItem())
                dispatchAsync(sm, TestAction.A1)
                assertEquals(TestState.S1, expectItem())
                dispatchAsync(sm, TestAction.A2)
                assertEquals(TestState.S2, expectItem())
            }
        }
    }
}
