package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import com.freeletics.flowredux.suspendTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CollectWhileInStateEffectTest {

    @Test
    fun `collectWhileInStateEffect stops after having moved to next state`() = suspendTest {

        val recordedValues = mutableListOf<Int>()

        val delayMs = 20L

        val sm = StateMachine {
            inState<TestState.Initial> {
                val flow = flow {
                    emit(1)
                    delay(delayMs)
                    emit(2)
                    delay(delayMs)
                    emit(3)
                }

                collectWhileInStateEffect(flow) { v, _ ->
                    recordedValues.add(v)
                }

                collectWhileInState(flow {
                    delay(delayMs / 2)
                    emit(Unit)
                }) { _, state ->
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            assertEquals(TestState.S1, awaitItem())
        }
        delay(delayMs * 3) // wait until all flow emission could in theory be happened
        assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
    }


    @Test
    fun `collectWhileInStateEffect with flowBuilder stops after having moved to next state`() =
        suspendTest {

            val delayMs = 20L

            val recordedValues = mutableListOf<Int>()

            val sm = StateMachine {
                inState<TestState.Initial> {
                    collectWhileInState({
                        it.flatMapConcat {
                            flow {
                                delay(delayMs / 2)
                                emit(Unit)
                            }
                        }
                    }) { _, state ->
                        state.override { TestState.S1 }
                    }

                    collectWhileInStateEffect({
                        it.flatMapConcat {
                            flow {
                                emit(1)
                                delay(delayMs)
                                emit(2)
                                delay(delayMs)
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
            delay(delayMs * 3)
            assertEquals(listOf(1), recordedValues) // 2,3 is not emitted
        }
}
