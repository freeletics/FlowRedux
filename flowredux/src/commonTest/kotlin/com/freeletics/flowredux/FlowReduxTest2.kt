package com.freeletics.flowredux

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class FlowReduxTest2 {

    @Test
    fun turbine() = suspendTest {
        async {
            flowOf(1, 2, 3).test {
                assertEquals(1, expectItem())
                assertEquals(2, expectItem())
                assertEquals(3, expectItem())
                expectComplete()
            }
        }
    }


    @Test
    fun `store without side effects`() = suspendTest {
        async {
            flow {
                emit(1)
                emit(2)
            }.reduxStore({ "" }, listOf()) { state, action ->
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
    fun `store with unconnected empty side effect`() = suspendTest {
        val sideEffect1: SideEffect<String, Int> = { _, _ -> emptyFlow() }

        async {
            val store = flow {
                emit(1)
                emit(2)
            }.reduxStore({ "" }, listOf(sideEffect1)) { state, action ->
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
    fun `store with empty side effect`() = suspendTest {

        async {

            var counter = 0

            val sideEffect1Actions = mutableListOf<Int>()
            val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat {
                    println("sideEffect")
                    sideEffect1Actions.add(it)
                    emptyFlow<Int>()
                }
            }

            flow {
                emit(++counter)
                emit(++counter)
            }.reduxStore({ "" }, listOf(sideEffect1)) { state, action ->
                println("Reducer $state $action")
                state + action
            }.test {
                assertEquals("", expectItem())
                assertEquals("1", expectItem())
                assertEquals("12", expectItem())
                expectComplete()
                assertEquals(listOf(1, 2), sideEffect1Actions)
            }
        }
    }

    @Test
    fun `store with 2 empty side effects`() = suspendTest {

        async {

            val sideEffect1Actions = mutableListOf<Int>()
            val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat { sideEffect1Actions.add(it); emptyFlow<Int>() }
            }
            val sideEffect2Actions = mutableListOf<Int>()
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
                assertEquals(listOf(1, 2), sideEffect1Actions)
                assertEquals(listOf(1, 2), sideEffect2Actions)
            }
        }
    }

    @Test
    fun `store with 2 simple side effects`() = suspendTest {
        async {

            val sideEffect1Actions = mutableListOf<Int>()
            val sideEffect1: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat {
                    sideEffect1Actions.add(it)
                    if (it < 6) {
                        flowOf(6)
                    } else {
                        emptyFlow()
                    }
                }
            }
            val sideEffect2Actions = mutableListOf<Int>()
            val sideEffect2: SideEffect<String, Int> = { actions, _ ->
                actions.flatMapConcat {
                    sideEffect2Actions.add(it)
                    if (it < 6) {
                        flowOf(7)
                    } else {
                        emptyFlow()
                    }
                }
            }

            val store = flow {
                emit(1)
                emit(2)
            }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
                state + action
            }.test {
                assertEquals("", expectItem())
                assertEquals("1", expectItem())
                assertEquals("12", expectItem())
                assertEquals("126", expectItem())
                assertEquals("1266", expectItem())
                assertEquals("12667", expectItem())
                assertEquals("126677", expectItem())
                expectComplete()
                assertEquals(listOf(1, 2, 6, 6, 7, 7), sideEffect1Actions)
                assertEquals(listOf(1, 2, 6, 6, 7, 7), sideEffect2Actions)
            }
        }
    }

    /*
    @Test
    @Ignore
    fun `store with 2 multi value side effects`() = runBlockingTest {
        val counter = AtomicInteger()

        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat {
                sideEffect1Actions.add(it)
                if (it < 6) {
                    flowOf(6, 7)
                } else {
                    emptyFlow()
                }
            }
        }
        val sideEffect2Actions = mutableListOf<Int>()
        val sideEffect2: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat {
                sideEffect2Actions.add(it)
                if (it < 6) {
                    flowOf(8, 9)
                } else {
                    emptyFlow()
                }
            }
        }

        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf(
                "",
                "1",
                "16",
                "167",
                "1678",
                "16789",
                "167892",
                "1678926",
                "16789267",
                "167892678",
                "1678926789"
        )
        sideEffect1Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
        sideEffect2Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
    }

    @Test
    @Ignore("select is biased which will cause the first sideeffect to be prioritized, order: 1687926879")
    fun `store with 2 side effects which react to side effect actions`() = runBlockingTest {

        val counter = AtomicInteger()

        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat {
                println("SF0: got $it")
                sideEffect1Actions.add(it)
                if (it < 6) {
                    flowOf(6)
                } else if (it < 7) {
                    flowOf(8)
                } else {
                    emptyFlow()
                }
            }
        }
        val sideEffect2Actions = mutableListOf<Int>()
        val sideEffect2: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat {
                println("SF1: got $it")
                sideEffect2Actions.add(it)
                if (it < 6) {
                    flowOf(7)
                } else if (it < 7) {
                    flowOf(9)
                } else {
                    emptyFlow()
                }
            }
        }

        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf(
                "",
                "1",
                "16",
                "167",
                "1678",
                "16789",
                "167892",
                "1678926",
                "16789267",
                "167892678",
                "1678926789"
        )
        sideEffect1Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
        sideEffect2Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
    }

    */
}