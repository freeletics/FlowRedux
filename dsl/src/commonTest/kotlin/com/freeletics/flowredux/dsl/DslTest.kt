package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
class DslTest {

    @Test
    fun `empty statemachine just emits initial state`() = suspendTest {
        val sm = StateMachine { }
        launch {
            sm.state.test {
                assertEquals(TestState.Initial, expectItem())
            }
        }
    }

    @Test
    fun `on action gets triggered and moves to next state`() = suspendTest {

        launch {
            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _ ->
                        { TestState.S1 }
                    }
                }

                inState<TestState.S1> {
                    on<TestAction.A2> { _, _ ->
                        { TestState.S2 }
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

    @Test
    fun `collectWhileInState stops after having moved to next state`() {

        val recordedValues = mutableListOf<Int>()

        suspendTest {


            val sm = StateMachine {
                inState<TestState.Initial> {
                    collectWhileInState(flow {
                        emit(1)
                        delay(10)
                        emit(2)
                        delay(10)
                        emit(3)
                    }) { v, _ ->
                        recordedValues.add(v)
                        return@collectWhileInState { TestState.S1 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(TestState.Initial, expectItem())
                    assertEquals(TestState.S1, expectItem())
                }
            }
        }
        assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
    }

    @Test
    fun `move from collectWhileInState to next state with action`() = suspendTest {

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(flowOf(1)) { _, _ ->
                    { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> { _, _ ->
                    { TestState.S2 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { _, _ ->
                    { TestState.S1 }
                }
            }
        }

        launch {
            sm.state.test {
                assertEquals(TestState.Initial, expectItem())
                assertEquals(TestState.S1, expectItem())

                dispatchAsync(sm, TestAction.A1)
                assertEquals(TestState.S2, expectItem())

                dispatchAsync(sm, TestAction.A2)
                assertEquals(TestState.S1, expectItem())

                dispatchAsync(sm, TestAction.A1)
                assertEquals(TestState.S2, expectItem())

                dispatchAsync(sm, TestAction.A2)
                assertEquals(TestState.S1, expectItem())
            }
        }
    }


    @Test
    fun `on entering the same state doesnt tringer onEnter again`() {
        suspendTest {
            var s1Entered = 0
            val sm = StateMachine {
                inState<TestState.Initial> {
                    onEnter { _ ->
                        { TestState.S1 }
                    }
                }

                inState<TestState.S1> {
                    onEnter { _ ->
                        s1Entered++
                        { it }
                    }
                    on<TestAction.A1> { _, _ -> { TestState.S1 } }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(TestState.Initial, expectItem())
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

    /*
    @Test
    fun `on TestAction triggers in state setState executes while being in same state`() {
        var setStateCalled = 0
        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, _, setState ->
                    setState { setStateCalled++; it }
                    setState { setStateCalled++; it }
                }
            }
        }

        val state = sm.state.testOverTime()
        sm.dispatchAsync(TestAction.A1)
        state shouldEmitNext TestState.Initial

        Assert.assertEquals(2, setStateCalled)
    }
     */


    private fun dispatchAsync(sm: FlowReduxStateMachine<TestState, TestAction>, action: TestAction) {
        GlobalScope.launch {
            sm.dispatch(action)
        }
    }
}
