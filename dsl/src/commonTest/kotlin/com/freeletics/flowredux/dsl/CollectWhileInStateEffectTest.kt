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
import kotlinx.coroutines.flow.flatMapConcat

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class CollectWhileInStateEffectTest {

    @Test
    fun `collectWhileInStateEffect stops after having moved to next state`() = suspendTest {

        val recordedValues = mutableListOf<Int>()

        val sm = StateMachine {
            inState<TestState.Initial> {
                val flow = flow {
                    emit(1)
                    delay(10)
                    emit(2)
                    delay(10)
                    emit(3)
                }

                collectWhileInStateEffect(flow) { v, _ ->
                    recordedValues.add(v)
                }

                collectWhileInState(flow {
                    delay(5)
                    emit(1)
                }) { _, _ ->
                    OverrideState(TestState.S1)
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.S1, awaitItem())
        }
        assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
    }


    @Test
    fun `collectWhileInStateEffect with flowBuilder stops after having moved to next state`() =
        suspendTest {

            val recordedValues = mutableListOf<Int>()

            val sm = StateMachine {
                inState<TestState.Initial> {
                    collectWhileInState({
                        it.flatMapConcat {
                            flow {
                                delay(7)
                                emit(1)
                            }
                        }
                    }) { _, _ ->
                        OverrideState(TestState.S1)
                    }

                    collectWhileInStateEffect({
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
                    }
                }

            }

            sm.state.test {
                assertEquals(TestState.Initial, awaitItem())
                assertEquals(TestState.S1, awaitItem())
            }
            assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
        }
}
