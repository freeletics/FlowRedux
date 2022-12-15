package com.freeletics.flowredux

import app.cash.turbine.awaitComplete
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FlowReduxTest {

    @Test
    fun `initial state is emitted even without actions as input`() = runTest {
        var reducerInvocations = 0

        emptyFlow<Int>()
            .reduxStore({ 0 }, emptyList()) { state, _ ->
                reducerInvocations++
                state + 1
            }
            .test {
                assertEquals(0, awaitItem())
                awaitComplete()
            }

        assertEquals(0, reducerInvocations)
    }

    @Test
    fun `store without side effects just runs reducer`() = runTest {
        flow {
            emit(1)
            emit(2)
        }.reduxStore({ "" }, listOf()) { state, action ->
            state + action
        }.test {
            assertEquals("", awaitItem())
            assertEquals("1", awaitItem())
            assertEquals("12", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `store with empty side effect that emits nothing`() = runTest {
        val sideEffect1Actions = mutableListOf<Int>()

        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat {
                sideEffect1Actions.add(it)
                emptyFlow()
            }
        }

        flow {
            emit(1)
            emit(2)
        }.reduxStore({ "" }, listOf(sideEffect1)) { state, action ->
            state + action
        }.test {
            assertEquals("", awaitItem())
            assertEquals("1", awaitItem())
            assertEquals("12", awaitItem())
        }

        assertEquals(listOf(1, 2), sideEffect1Actions)
    }

    @Test
    fun `store with 2 side effects and they emit nothing`() = runTest {
        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect2Actions = mutableListOf<Int>()

        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat { sideEffect1Actions.add(it); emptyFlow() }
        }
        val sideEffect2: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat { sideEffect2Actions.add(it); emptyFlow() }
        }

        flow {
            emit(1)
            emit(2)
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }.test {
            assertEquals("", awaitItem())
            assertEquals("1", awaitItem())
            assertEquals("12", awaitItem())
        }

        assertEquals(listOf(1, 2), sideEffect1Actions)
        assertEquals(listOf(1, 2), sideEffect2Actions)
    }

    @Test
    fun `store with 2 simple side effects`() = runTest {
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

        val flow = MutableSharedFlow<Int>()

        flow.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }.test {
            // Initial State emission
            assertEquals("", awaitItem())

            // emission of 1
            flow.emit(1)
            assertEquals("1", awaitItem())
            assertEquals("16", awaitItem())
            assertEquals("167", awaitItem())

            // emission of 2
            flow.emit(2)
            assertEquals("1672", awaitItem())
            assertEquals("16726", awaitItem())
            assertEquals("167267", awaitItem())
        }
    }

    @Test
    fun `store with 2 simple side effects synchronous`() = runTest {
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
            emit(2)
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }.test {
            // Initial State emission
            assertEquals("", awaitItem())

            // emission of 1
            assertEquals("1", awaitItem())
            // emission of 2
            assertEquals("12", awaitItem())
            // side effect actions in response to 1
            assertEquals("126", awaitItem())
            assertEquals("1267", awaitItem())
            // side effect actions in response to 2
            assertEquals("12676", awaitItem())
            assertEquals("126767", awaitItem())
        }
    }

    @Test
    fun `canceling the flow of input actions also cancels all side effects`() = runTest {
        var sideEffect1Started = false
        var sideEffect2Started = false
        var sideEffect1Ended = false
        var sideEffect2Ended = false

        val signal = Channel<Unit>()

        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.map {
                sideEffect1Started = true
                signal.awaitComplete()
                sideEffect1Ended = true
                throw Exception("This should never be reached")
            }
        }
        val sideEffect2: SideEffect<String, Int> = { actions, _ ->
            actions.map {
                sideEffect2Started = true
                signal.awaitComplete()
                sideEffect2Ended = true
                throw Exception("This should never be reached")
            }
        }

        flow {
            emit(1)
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }.test {
            assertEquals("", awaitItem())
            assertEquals("1", awaitItem())
        }

        signal.close()

        assertTrue(sideEffect1Started)
        assertFalse(sideEffect1Ended)
        assertTrue(sideEffect2Started)
        assertFalse(sideEffect2Ended)
    }
}
