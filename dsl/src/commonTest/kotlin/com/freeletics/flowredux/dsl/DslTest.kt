package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
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
                assertEquals(State.Initial, expectItem())
            }
        }
    }

    @Test
    fun `on action gets triggered and moves to next state`() = suspendTest {

        launch {
            val sm = StateMachine {
                inState<State.Initial> {
                    on<Action.A1> { _, _, setState ->
                        setState { State.S1 }
                    }
                }

                inState<State.S1> {
                    on<Action.A2> { _, _, setState ->
                        setState { State.S2 }
                    }

                }

            }
            sm.state.test {
                assertEquals(State.Initial, expectItem())
                sm.dispatchAsync(Action.A1)
                assertEquals(State.S1, expectItem())
                sm.dispatchAsync(Action.A2)
                assertEquals(State.S2, expectItem())
            }
        }
    }

    @Test
    fun `collectWhileInState stops after having moved to next state`() {

        val recordedValues = mutableListOf<Int>()

        suspendTest {


            val sm = StateMachine {
                inState<State.Initial> {
                    collectWhileInState(flow {
                        emit(1)
                        delay(10)
                        emit(2)
                        delay(10)
                        emit(3)
                    }) { v, _, setState ->
                        recordedValues.add(v)
                        if (v == 2)
                            setState { State.S1 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(State.Initial, expectItem())
                    assertEquals(State.S1, expectItem())
                }
            }
        }
        assertEquals(listOf(1, 2), recordedValues) // 3 is not emitted
    }

    @Test
    fun `move from collectWhileInState to next state with action`() = suspendTest {

        val sm = StateMachine {
            inState<State.Initial> {
                collectWhileInState(flowOf(1)) { _, _, setState ->
                    setState { State.S1 }
                }
            }

            inState<State.S1> {
                on<Action.A1> { _, _, setState ->
                    setState { State.S2 }
                }
            }

            inState<State.S2> {
                on<Action.A2> { _, _, setState ->
                    setState { State.S1 }

                }
            }
        }

        launch {
            sm.state.test {
                assertEquals(State.Initial, expectItem())
                assertEquals(State.S1, expectItem())

                sm.dispatchAsync(Action.A1)
                assertEquals(State.S2, expectItem())

                sm.dispatchAsync(Action.A2)
                assertEquals(State.S1, expectItem())

                sm.dispatchAsync(Action.A1)
                assertEquals(State.S2, expectItem())

                sm.dispatchAsync(Action.A2)
                assertEquals(State.S1, expectItem())
            }
        }
    }

    @Test
    fun `onEnter in a state triggers but doesnt stop execution on leaving state`() {
        val order = ArrayList<Int>()
        suspendTest {
            val sm = StateMachine {
                inState<State.Initial> {
                    onEnter { _, setState ->
                        order.add(0)
                        setState { State.S1 }
                        delay(50)
                        order.add(1)
                    }
                }

                inState<State.S1> {
                    onEnter { _, setState ->
                        order.add(2)
                        delay(100)
                        setState { State.S2 }
                        order.add(3)
                    }
                }

                inState<State.S2> {
                    onEnter { _, _ ->
                        order.add(4)
                    }

                    on<Action.A1> { _, _, setState ->
                        setState { State.S2 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(State.Initial, expectItem())
                    assertEquals(State.S1, expectItem())
                    assertEquals(State.S2, expectItem())
                    sm.dispatchAsync(Action.A1)
                    delay(20) // wait for 20 ms before checking that no events
                    expectNoEvents()
                }
            }
        }
        assertEquals(listOf(0, 2, 1, 3, 4), order)
    }

    @Test
    fun `on entering the same state doesnt tringer onEnter again`() {
        suspendTest {
            var s1Entered = 0
            val sm = StateMachine {
                inState<State.Initial> {
                    onEnter { _, setState -> setState { State.S1 } }
                }

                inState<State.S1> {
                    onEnter { _, _ -> s1Entered++ }
                    on<Action.A1> { _, _, setState -> setState { State.S1 } }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(State.Initial, expectItem())
                    assertEquals(State.S1, expectItem())
                    repeat(2) {
                        sm.dispatchAsync(Action.A1) // Causes state transition to S1 again which is already current
                        expectNoEvents()
                        assertEquals(1, s1Entered)
                    }
                }
            }
        }
    }

    /*
    @Test
    fun `on Action triggers in state setState executes while being in same state`() {
        var setStateCalled = 0
        val sm = StateMachine {
            inState<State.Initial> {
                on<Action.A1> { _, _, setState ->
                    setState { setStateCalled++; it }
                    setState { setStateCalled++; it }
                }
            }
        }

        val state = sm.state.testOverTime()
        sm.dispatchAsync(Action.A1)
        state shouldEmitNext State.Initial

        Assert.assertEquals(2, setStateCalled)
    }
     */

    @Test
    fun `on Action changes state than second setState doesn't trigger anymore`() {
        var setStateCalled = 0

        suspendTest {
            val sm = StateMachine {
                inState<State.Initial> {
                    on<Action.A1> { _, _, setState ->
                        setState { setStateCalled++; State.S1 }
                        setState { setStateCalled++; State.S2 }
                    }
                }
                inState<State.S1> {
                    onEnter { _, setState ->
                        delay(100)
                        setState { State.S3 }
                    }
                }
            }

            launch {
                val state = sm.state.test {
                    sm.dispatchAsync(Action.A1)
                    assertEquals(State.Initial, expectItem())
                    assertEquals(State.S1, expectItem())
                    assertEquals(State.S3, expectItem())
                }
            }

        }
        assertEquals(1, setStateCalled)
    }

    @Test
    fun `setState with runIf returning false doesnt change state`() {

        var setS1Called = false
        var a1Dispatched = false

        suspendTest {
            val sm = StateMachine {
                inState<State.Initial> {
                    on<Action.A1> { _, _, setState ->
                        a1Dispatched = true
                        setState(runIf = { false }) { setS1Called = true; State.S1 }
                    }

                    on<Action.A2> { _, _, setState ->
                        delay(100) // ensure that A1 setState{ } would have time be executed
                        setState { State.S2 }
                    }
                }
            }

            launch {
                sm.state.test {
                    assertEquals(State.Initial, expectItem())

                    sm.dispatchAsync(Action.A1)
                    sm.dispatchAsync(Action.A2)

                    assertEquals(State.S2, expectItem())
                }
            }
        }
        assertFalse(setS1Called)
        assertTrue(a1Dispatched)
    }
}
