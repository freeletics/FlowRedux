package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class CollectWhileInStateTest {

    @Test
    fun `collectWhileInState stops after having moved to next state`() = suspendTest {

        val recordedValues = mutableListOf<Int>()

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(flow {
                    emit(1)
                    delay(10)
                    emit(2)
                    delay(10)
                    emit(3)
                }) { v, _ ->
                    recordedValues.add(v)
                    return@collectWhileInState OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            // switch to from Initial to S1 is immediate, before we start collecting
            assertEquals(TestState.S1, expectItem())
        }
        assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
    }

    @Test
    fun `move from collectWhileInState to next state with action`() = suspendTest {

        val sm = StateMachine {
            inState<TestState.Initial> {
                collectWhileInState(flowOf(1)) { _, _ ->
                    OverrideState(TestState.S1)
                }
            }

            inState<TestState.S1> {
                on<TestAction.A1> { _, _ ->
                    OverrideState(TestState.S2)
                }
            }

            inState<TestState.S2> {
                on<TestAction.A2> { _, _ ->
                    OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            // switch to from Initial to S1 is immediate, before we start collecting
            assertEquals(TestState.S1, expectItem())

            dispatchAsync(sm, TestAction.A1)
            assertEquals(TestState.S2, expectItem())

            dispatchAsync(sm, TestAction.A2)
            assertEquals(TestState.S1, expectItem())

            dispatchAsync(sm, TestAction.A1)
            assertEquals(TestState.S2, expectItem())

            dispatchAsync(sm, TestAction.A2)
            assertEquals(TestState.S1, expectItem())
        }
    }
}
