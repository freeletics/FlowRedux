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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("deprecation")
internal class CollectWhileTest {
    @Test
    fun collectWhileInStateStopsAfterHavingMovedToNextState() = runTest {
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(values) { v ->
                    recordedValues.send(v)
                    override { TestState.S1 }
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
    fun collectWhileInStateWithBuilderStopsAfterHavingMovedToNextState() = runTest {
        val values = MutableSharedFlow<Int>()
        val recordedValues = Channel<Int>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState({ values }) { v ->
                    recordedValues.send(v)
                    override { TestState.S1 }
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
    fun moveFromCollectWhileInStateToNextStateWithAction() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(flowOf(1)) {
                    override { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> {
                    override { TestState.S2 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> {
                    override { TestState.S1 }
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
    fun moveFromCollectWhileInStateWithBuilderToNextStateWithAction() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState({ flowOf(1) }) {
                    override { TestState.S1 }
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> {
                    override { TestState.S2 }
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> {
                    override { TestState.S1 }
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
    fun collectWhileInStateWithBuilderReceivesInitialState() = runTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    override { TestState.GenericState("", 1) }
                }
            }
            inState<TestState.GenericState> {
                collectWhileInState({ flowOf(it.anInt * 10) }) { value ->
                    override {
                        TestState.GenericState(aString = aString + value, anInt = value)
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.GenericState("", 1), awaitItem())
            assertEquals(TestState.GenericState("10", 10), awaitItem())
        }
    }
}
