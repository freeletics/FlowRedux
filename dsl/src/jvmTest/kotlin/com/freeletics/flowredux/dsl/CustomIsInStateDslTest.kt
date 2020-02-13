package com.freeletics.flowredux.dsl

import io.kotlintest.should
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.junit.Assert
import org.junit.Test

class CustomIsInStateDslTest {

    @Test
    fun `on Action triggers only while in custom state`() {
        var counter1 = 0
        var counter2 = 0
        val gs1 = State.GenericState("asd", 1)
        val gs2 = State.GenericState("foo", 2)

        val sm = StateMachine {
            inState<State.Initial> {
                on<Action.A1> { _, _, setState ->
                    setState { gs1 }
                }
            }
            inState(isInState = { it is State.GenericState && it.anInt == 1 }) {
                on<Action.A1> { _, _, setState ->
                    counter1++
                    setState { gs2 }
                }
            }

            inState(isInState = { it is State.GenericState && it.anInt == 2 }) {
                on<Action.A1> { _, _, setState ->
                    delay(20) // wait for some time to see if not other state above triggers
                    counter2++
                    setState { State.S1 }
                }
            }
        }

        val state = sm.state.testOverTime()

        state shouldEmitNext State.Initial
        sm.dispatchAsync(Action.A1)
        state shouldEmitNext gs1
        sm.dispatchAsync(Action.A1)
        state shouldEmitNext gs2
        sm.dispatchAsync(Action.A1)
        state shouldEmitNext State.S1

        Assert.assertEquals(1, counter1)
        Assert.assertEquals(1, counter2)
    }

    @Test
    fun `collectWhileInState stops when leaving custom state`() {
        val gs1 = State.GenericState("asd", 1)
        val gs2 = State.GenericState("2", 2)
        var reached = false

        val sm = StateMachine {
            inState<State.Initial> {
                on<Action.A1> { _, _, setState ->
                    setState { gs1 }
                }
            }
            inState(isInState = { it is State.GenericState && it.anInt == 1 }) {
                collectWhileInState(flow {
                    emit(2)
                    delay(20)
                    reached = true
                    Assert.fail("This should never be reached")
                    emit(9999)
                }) { value, _, setState ->
                    setState { State.GenericState(value.toString(), value) }
                }
            }

            inState(isInState = { it is State.GenericState && it.anInt == 2 }){
                onEnter { _, setState ->
                    delay(50) // Wait until collectWhileInState succeeded
                    setState { State.S1}
                }
            }
        }

        val state = sm.state.testOverTime()
        state shouldEmitNext State.Initial
        sm.dispatchAsync(Action.A1)
        state shouldEmitNext gs1
        state shouldEmitNext gs2
        state shouldEmitNext State.S1

        Assert.assertFalse(reached)
    }
}