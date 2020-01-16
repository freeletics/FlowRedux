package com.freeletics.flowredux.dsl

import com.freeletics.flow.testovertime.record
import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

// TODO fix FlowRecorder and migrate tast from .testOverTime() to .record()
class DslTest {

    private val timeout20Milliseconds = TimeoutConfig(
        timeout = 20,
        timeoutTimeUnit = TimeUnit.MILLISECONDS
    )

    @Test
    fun `empty statemachine just emits initial state`() {
        val sm = StateMachine { }
        val state = sm.state.record()

        state shouldEmitNext State.Initial
    }

    @Test
    fun `on action gets triggered and moves to next state`() {
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
        val state = sm.state.testOverTime()

        state shouldEmitNext State.Initial

        sm.dispatchAsync(Action.A1)
        state shouldEmitNext State.S1

        sm.dispatchAsync(Action.A2)
        state shouldEmitNext State.S2
    }

    @Test
    fun `observe while in state stops after having moved to next state`() {

        val recordedValues = mutableListOf<Int>()
        val sm = StateMachine {
            inState<State.Initial> {
                observeWhileInState(flow {
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
        val state = sm.state.testOverTime()

        state.shouldEmitNext(
            State.Initial,
            State.S1
        )

        state.shouldNotEmitMoreValues()
        Assert.assertEquals(listOf(1, 2), recordedValues) // 3 is not emitted
    }

    @Test
    fun `move from observeWhileInState to next state with action`() {

        val sm = StateMachine {
            inState<State.Initial> {
                observeWhileInState(flowOf(1)) { _, _, setState ->
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

        val state = sm.state.testOverTime()

        state.shouldEmitNext(
            State.Initial,
            State.S1
        )

        sm.dispatchAsync(Action.A1)
        state shouldEmitNext State.S2

        sm.dispatchAsync(Action.A2)
        state shouldEmitNext State.S1

        sm.dispatchAsync(Action.A1)
        state shouldEmitNext State.S2

        sm.dispatchAsync(Action.A2)
        state shouldEmitNext State.S1
    }

    @Test
    fun `onEnter in a state triggers but doesnt stop execution on leaving state`() {
        val order = ArrayList<Int>()
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

        val state = sm.state.testOverTime()

        state.shouldEmitNext(State.Initial, State.S1, State.S2)

        sm.dispatchAsync(Action.A1)
        state.shouldNotHaveEmittedSinceLastCheck(timeout20Milliseconds)

        Assert.assertEquals(listOf(0, 2, 1, 3, 4), order)
    }

    fun `on entering the same state doesnt tringer onEnter again`() {
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

        val state = sm.state.testOverTime()
        state.shouldEmitNext(State.Initial, State.S1)

        repeat(2) {
            sm.dispatchAsync(Action.A1) // Causes state transition to S1 again which is already current
            state.shouldNotHaveEmittedSinceLastCheck(timeout20Milliseconds)
            Assert.assertEquals(1, s1Entered)
        }
    }
}

private sealed class Action {
    object A1 : Action()
    object A2 : Action(){
        fun foo(){}
    }
}

private sealed class State {
    object Initial : State()
    object S1 : State()
    object S2 : State()
}

private class StateMachine(
    builderBlock: FlowReduxStoreBuilder<State, Action>.() -> Unit
) : FlowReduxStateMachine<State, Action>(
    CommandLineLogger,
    State.Initial
) {

    init {
        spec(builderBlock)
    }
}

private fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.dispatchAsync(action: A) {
    val sm = this
    GlobalScope.launch {
        sm.dispatch(action)
    }
}

private object CommandLineLogger : FlowReduxLogger {
    override fun log(message: String) {
        println(message)
    }
}
