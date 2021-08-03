package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flatMapConcat

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class CollectStateWhileInStateTest {

    @Test
    fun `collectStateWhileInState stops after having moved to next state`() = suspendTest {

        val recordedValues = mutableListOf<Int>()

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState({
                    it.flatMapConcat {
                        flow {
                            emit(1)
                            delay(10)
                            emit(2)
                            delay(10)
                            emit(3)
                        }
                    }
                }) { v, _ ->
                    recordedValues.add(v)
                    OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, expectItem())
            assertEquals(TestState.S1, expectItem())
        }
        assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
    }

    @Test
    fun `collectStateWhileInState receives any GenericState state update`() = suspendTest {
        val sm = StateMachine {
            inState<TestState.Initial> {
                onEnter {
                    OverrideState(TestState.GenericState("", 0))
                }
            }
            inState<TestState.GenericState> {
                collectWhileInState({
                    it.flatMapConcat { state ->
                        flow {
                            emit(1 + 10 * state.anInt)
                        }
                    }
                }) { value, _ ->
                    MutateState<TestState.GenericState, TestState> {
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
            assertEquals(TestState.Initial, expectItem())
            assertEquals(TestState.GenericState("", 0), expectItem())
            assertEquals(TestState.GenericState("1", 1), expectItem())
            assertEquals(TestState.GenericState("111", 11), expectItem())
            assertEquals(TestState.GenericState("111111", 111), expectItem())
            assertEquals(TestState.GenericState("1111111111", 1111), expectItem())
            assertEquals(TestState.S1, expectItem())
        }
    }
}
