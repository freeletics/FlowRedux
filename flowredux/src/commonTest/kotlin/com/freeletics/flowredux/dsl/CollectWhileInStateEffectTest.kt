package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class CollectWhileInStateEffectTest {

    @Test
    fun collectWhileInStateEffectStopsAfterHavingMovedToNextState() = runTest {
        val stateChange = MutableSharedFlow<Unit>()
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInStateEffect(values) { v, _ ->
                    recordedValues.send(v)
                }

                collectWhileInState(stateChange) { _, state ->
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            values.emit(1)
            stateChange.emit(Unit)
            assertEquals(TestState.S1, awaitItem())

            values.emit(2)
            values.emit(3)
            recordedValues.consumeAsFlow().test {
                assertEquals(1, awaitItem())
            }
        }
    }

    @Test
    fun collectWhileInStateEffectWithFlowBuilderStopsAfterHavingMovedToNextState() = runTest {
        val stateChange = MutableSharedFlow<Unit>()
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState({
                    it.flatMapConcat {
                        stateChange
                    }
                }) { _, state ->
                    state.override { TestState.S1 }
                }

                collectWhileInStateEffect({ it.flatMapConcat { values } }) { v, _ ->
                    recordedValues.send(v)
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            values.emit(1)
            stateChange.emit(Unit)
            assertEquals(TestState.S1, awaitItem())

            values.emit(2)
            values.emit(3)
            recordedValues.consumeAsFlow().test {
                assertEquals(1, awaitItem())
            }
        }
    }
}
