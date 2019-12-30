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

// TODO fix FlowRecorder and migrate tast from .testOverTime() to .record()
class DslTest {

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
}

private sealed class Action {
    object A1 : Action()
    object A2 : Action()
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
