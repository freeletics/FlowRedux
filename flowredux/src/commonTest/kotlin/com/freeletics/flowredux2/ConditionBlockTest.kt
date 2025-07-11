package com.freeletics.flowredux2

import app.cash.turbine.test
import com.freeletics.flowredux2.sideeffects.StateChangeCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConditionBlockTest {
    @Test
    fun onActionTriggersOnlyWhileInCustomCondition() = runTest {
        var counter1 = 0
        var counter2 = 0

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("foo", 2)

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                condition({ it.anInt == 1 }) {
                    on<TestAction.A1> {
                        counter1++
                        override { gs2 }
                    }
                }

                condition({ it.anInt == 2 }) {
                    on<TestAction.A1> {
                        counter2++
                        override { TestState.S1 }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs2, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())
        }

        assertEquals(1, counter1)
        assertEquals(1, counter2)
    }

    @Test
    fun collectWhileInStateStopsWhenLeavingCustomCondition() = runTest {
        var cancellation: Throwable? = null

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("2", 2)

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                condition({ it.anInt == 1 }) {
                    collectWhileInState(
                        flow {
                            emit(2)
                            try {
                                awaitCancellation()
                            } catch (t: Throwable) {
                                cancellation = t
                                throw t
                            }
                        },
                    ) {
                        override { TestState.GenericState(it.toString(), it) }
                    }
                }

                condition({ it.anInt == 2 }) {
                    onEnter {
                        return@onEnter override { TestState.S1 }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            assertEquals(gs2, awaitItem())
            assertIs<StateChangeCancellationException>(cancellation)
            assertEquals(TestState.S1, awaitItem())
        }
    }
}
