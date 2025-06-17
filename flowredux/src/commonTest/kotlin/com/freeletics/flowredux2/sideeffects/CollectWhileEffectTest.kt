package com.freeletics.flowredux2.sideeffects

import app.cash.turbine.test
import com.freeletics.flowredux2.StateMachine
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
internal class CollectWhileEffectTest {
    @Test
    fun collectWhileInStateEffectStopsAfterHavingMovedToNextState() = runTest {
        val stateChange = MutableSharedFlow<Unit>()
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInStateEffect(values) { v ->
                    recordedValues.send(v)
                }

                collectWhileInState(stateChange) {
                    override { TestState.S1 }
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
    fun collectWhileInStateEffectWithBuilderStopsAfterHavingMovedToNextState() = runTest {
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<String>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    override { TestState.GenericState("a", 0) }
                }
            }
            inState<TestState.GenericState> {
                collectWhileInStateEffect({ initial -> values.map { "${initial.aString}$it" } }) { v ->
                    recordedValues.send(v)
                }

                on<TestAction.A1> { state ->
                    override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.GenericState("a", 0), awaitItem())
            values.emit(1)
            values.emit(2)
            values.emit(3)
            sm.dispatch(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())

            values.emit(4)
            values.emit(5)
        }
        recordedValues.consumeAsFlow().test {
            assertEquals("a1", awaitItem())
            assertEquals("a2", awaitItem())
            assertEquals("a3", awaitItem())
        }
    }
}
