package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CollectWhileInStateTest {

    @Test
    fun `collectWhileInState stops after having moved to next state`() = runTest {
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(values) { v, state ->
                    recordedValues.send(v)
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            values.emit(1)
            assertEquals(TestState.S1, awaitItem())

            values.emit(2)
            values.emit(3)
            recordedValues.consumeAsFlow().test {
                assertEquals(1, awaitItem())
            }
        }
    }

    @Test
    fun `collectWhileInState with flowBuilder stops after having moved to next state`() = runTest {
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState({ it.flatMapConcat { values } }) { v, state ->
                    recordedValues.send(v)
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            values.emit(1)
            assertEquals(TestState.S1, awaitItem())

            values.emit(2)
            values.emit(3)
            recordedValues.consumeAsFlow().test {
                assertEquals(1, awaitItem())
            }
        }
    }


    @Test
    fun `move from collectWhileInState to next state with action`() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(flowOf(1)) { _, state ->
                    state.override { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> { _, state ->
                    state.override { TestState.S2 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { _, state ->
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.S1, awaitItem())

            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S2, awaitItem())

            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S1, awaitItem())

            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S2, awaitItem())

            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S1, awaitItem())
        }
    }

    @Test
    fun `collectWhileInState flowBuilder receives any GenericState state update`() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    it.override { TestState.GenericState("", 0) }
                }
            }
            inState<TestState.GenericState> {
                collectWhileInState({
                    it.flatMapConcat { state ->
                        flow {
                            emit(1 + 10 * state.anInt)
                        }
                    }
                }) { value, state ->
                    state.override {
                        if (value < 10000) {
                            TestState.GenericState(aString = aString + value, anInt = value)
                        } else {
                            TestState.S1
                        }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.GenericState("", 0), awaitItem())
            assertEquals(TestState.GenericState("1", 1), awaitItem())
            assertEquals(TestState.GenericState("111", 11), awaitItem())
            assertEquals(TestState.GenericState("111111", 111), awaitItem())
            assertEquals(TestState.GenericState("1111111111", 1111), awaitItem())
            assertEquals(TestState.S1, awaitItem())
        }
    }
}
