package com.freeletics.flowredux

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class FlowReduxTest2 {

    @Test
    fun `initial state is emitted even without actions as input`() {
        var reducerInvocations = 0
        suspendTest {
            launch {
                emptyFlow<Int>()
                        .reduxStore({ 0 }, emptyList()) { state, action ->
                            reducerInvocations++
                            state + 1
                        }
                        .test {
                            assertEquals(0, expectItem())
                            expectComplete()
                        }
            }
        }

        assertEquals(0, reducerInvocations)
    }

    @Test
    fun `store without side effects just runs reducer`() = suspendTest {
        async {
            flow {
                emit(1)
                emit(2)
            }.reduxStore({ "" }, listOf(), CommandLineLogger) { state, action ->
                state + action
            }.test {
                assertEquals("", expectItem())
                assertEquals("1", expectItem())
                assertEquals("12", expectItem())
                expectComplete()
            }
        }

    }

    @Test
    fun `store with empty side effect that emits nothing`() {
        val sideEffect1Actions = mutableListOf<Int>()

        suspendTest {
            launch {

                val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                    actions.flatMapConcat {
                        sideEffect1Actions.add(it)
                        emptyFlow<Int>()
                    }
                }

                flow {
                    emit(1)
                    emit(2)
                }.reduxStore({ "" }, listOf(sideEffect1), CommandLineLogger) { state, action ->
                    state + action
                }.test {
                    assertEquals("", expectItem())
                    assertEquals("1", expectItem())
                    assertEquals("12", expectItem())
                    expectComplete()
                }
            }
        }

        assertEquals(listOf(1, 2), sideEffect1Actions)

    }

    @Test
    fun `store with 2 side effects and they emit nothing`() {
        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect2Actions = mutableListOf<Int>()

        suspendTest {

            launch {

                val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                    actions.flatMapConcat { sideEffect1Actions.add(it); emptyFlow<Int>() }
                }
                val sideEffect2: SideEffect<String, Int> = { actions, _ ->
                    actions.flatMapConcat { sideEffect2Actions.add(it); emptyFlow<Int>() }
                }

                flow {
                    emit(1)
                    emit(2)
                }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
                    state + action
                }.test {
                    assertEquals("", expectItem())
                    assertEquals("1", expectItem())
                    assertEquals("12", expectItem())
                    expectComplete()
                }
            }
        }

        assertEquals(listOf(1, 2), sideEffect1Actions)
        assertEquals(listOf(1, 2), sideEffect2Actions)
    }

    @Test
    fun `store with 2 simple side effects`() = suspendTest {
        async {
            val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat {
                    if (it < 6) {
                        flowOf(6)
                    } else {
                        emptyFlow()
                    }
                }
            }
            val sideEffect2: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat {
                    if (it < 6) {
                        flowOf(7)
                    } else {
                        emptyFlow()
                    }
                }
            }

            flow {
                emit(1)
                //delay(10)
                emit(2)
            }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
                state + action
            }.test {
                // Initial State emission
                assertEquals("", expectItem())

                // emission of 1
                assertEquals("1", expectItem())
                assertEquals("16", expectItem())
                assertEquals("167", expectItem())

                // emission of 2
                assertEquals("1672", expectItem())
                assertEquals("16726", expectItem())
                assertEquals("167267", expectItem())

                expectComplete()
            }
        }
    }

    @Test
    fun `canceling the flow of input actions also cancels all side effects`() {

        var sideEffect1Started = false
        var sideEffect2Started = false
        var sideEffect1Ended = false
        var sideEffect2Ended = false

        suspendTest {
            val job = launch {

                val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                    actions.map {
                        sideEffect1Started = true
                        delay(1000)
                        sideEffect1Ended = true
                        throw Exception("This should never be reached")
                    }
                }
                val sideEffect2: SideEffect<String, Int> = { actions, _ ->
                    actions.map {
                        sideEffect2Started = true
                        delay(1000)
                        sideEffect2Ended = true
                        throw Exception("This should never be reached")
                    }
                }

                flow {
                    emit(1)
                }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
                    state + action
                }.test {
                    assertEquals("", expectItem())
                    assertEquals("1", expectItem())
                }
            }

            launch {
                delay(100)
                job.cancel()
            }
        }

        assertTrue(sideEffect1Started)
        assertFalse(sideEffect1Ended)
        assertTrue(sideEffect2Started)
        assertFalse(sideEffect2Ended)
    }
}