package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.ExperimentalTime

@ExperimentalTime
class CustomIsInStateDslTest {

    @Test
    fun `on Action triggers only while in custom state`() {

        var counter1 = 0
        var counter2 = 0

        suspendTest {
            val gs1 = TestState.GenericState("asd", 1)
            val gs2 = TestState.GenericState("foo", 2)

            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _, setState ->
                        setState { gs1 }
                    }
                }
                inState(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                    on<TestAction.A1> { _, _, setState ->
                        counter1++
                        setState { gs2 }
                    }
                }

                inState(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                    on<TestAction.A1> { _, _, setState ->
                        delay(20) // wait for some time to see if not other state above triggers
                        counter2++
                        setState { TestState.S1 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(TestState.Initial, expectItem())
                    dispatchAsync(sm, TestAction.A1)
                    assertEquals(gs1, expectItem())
                    dispatchAsync(sm, TestAction.A1)
                    assertEquals(gs2, expectItem())
                    dispatchAsync(sm, TestAction.A1)
                    assertEquals(TestState.S1, expectItem())
                }
            }


        }

        assertEquals(1, counter1)
        assertEquals(1, counter2)
    }

    @Test
    fun `collectWhileInState stops when leaving custom state`() {

        var reached = false

        suspendTest {
            val gs1 = TestState.GenericState("asd", 1)
            val gs2 = TestState.GenericState("2", 2)

            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _, setState ->
                        setState { gs1 }
                    }
                }
                inState(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                    collectWhileInState(flow {
                        emit(2)
                        delay(20)
                        reached = true
                        fail("This should never be reached")
                        emit(9999)
                    }) { value, _, setState ->
                        setState { TestState.GenericState(value.toString(), value) }
                    }
                }

                inState(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                    onEnter { _, setState ->
                        delay(50) // Wait until collectWhileInState succeeded
                        setState { TestState.S1 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(TestState.Initial, expectItem())
                    dispatchAsync(sm, TestAction.A1)
                    assertEquals(gs1, expectItem())
                    assertEquals(gs2, expectItem())
                    assertEquals(TestState.S1, expectItem())
                }

            }
        }

        assertFalse(reached)
    }

    private fun dispatchAsync(sm: FlowReduxStateMachine<TestState, TestAction>, action: TestAction) {
        GlobalScope.launch {
            sm.dispatch(action)
        }
    }
}