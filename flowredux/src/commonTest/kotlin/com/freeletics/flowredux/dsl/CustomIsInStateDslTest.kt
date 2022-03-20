package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class CustomIsInStateDslTest {

    @Test
    fun `on Action triggers only while in custom state`() = suspendTest {

        var counter1 = 0
        var counter2 = 0

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("foo", 2)

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, _ ->
                    OverrideState(gs1)
                }
            }
            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                on<TestAction.A1> { _, _ ->
                    counter1++
                    OverrideState(gs2)
                }
            }

            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                on<TestAction.A1> { _, _ ->
                    delay(20) // wait for some time to see if not other state above triggers
                    counter2++
                    OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs2, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())
        }

        assertEquals(1, counter1)
        assertEquals(1, counter2)
    }

    @Test
    fun `collectWhileInState stops when leaving custom state`() = suspendTest {

        var reached = false

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("2", 2)

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, _ ->
                    OverrideState(gs1)
                }
            }
            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                collectWhileInState(flow {
                    emit(2)
                    delay(20)
                    reached = true
                    fail("This should never be reached")
                }) { value, _ ->
                    OverrideState(TestState.GenericState(value.toString(), value))
                }
            }

            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                onEnter {
                    delay(50) // Wait until collectWhileInState succeeded
                    return@onEnter OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            assertEquals(gs2, awaitItem())
            assertEquals(TestState.S1, awaitItem())
        }

        assertFalse(reached)
    }
}
