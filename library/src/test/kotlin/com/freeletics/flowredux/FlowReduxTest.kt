package com.freeletics.flowredux

import io.kotlintest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeout
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FlowReduxTest {

    private val counter = AtomicInteger()

    @Test
    fun `store without side effects`() = runBlockingTest {
        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf()) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf("", "1", "12")
    }

    @Test
    fun `store with unconnected empty side effect`() = runBlockingTest {
        val sideEffect1: SideEffect<String, Int> = { _, _ -> emptyFlow() }

        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf("", "1", "12")
    }

    @Test
    fun `store with empty side effect`() = runBlockingTest {
        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat { sideEffect1Actions.add(it); emptyFlow<Int>() }
        }

        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf("", "1", "12")
        sideEffect1Actions shouldContainExactly listOf(1, 2)
    }

    @Test
    fun `store with 2 empty side effects`() = runBlockingTest {
        val sideEffect1Actions = mutableListOf<Int>()
        val sideEffect1: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat { sideEffect1Actions.add(it); emptyFlow<Int>() }
        }
        val sideEffect2Actions = mutableListOf<Int>()
        val sideEffect2: SideEffect<String, Int> = { actions, _ ->
            actions.flatMapConcat { sideEffect2Actions.add(it); emptyFlow<Int>() }
        }

        val store = flow {
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf("", "1", "12")
        sideEffect1Actions shouldContainExactly listOf(1, 2)
        sideEffect2Actions shouldContainExactly listOf(1, 2)
    }

    @Test
    @Ignore
    fun `store with 2 simple side effects`() = runBlockingTest {
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
            emit(counter.incrementAndGet())
            emit(counter.incrementAndGet())
        }.reduxStore({ "" }, listOf(sideEffect1, sideEffect2)) { state, action ->
            state + action
        }

        store.toList() shouldContainExactly listOf("", "1", "16", "167", "1672", "16726", "167267")
        sideEffect1Actions shouldContainExactly listOf(1, 6, 7, 2, 6, 7)
        sideEffect2Actions shouldContainExactly listOf(1, 6, 7, 2, 6, 7)
    }

    @Test
    @Ignore
    fun `store with 2 multi value side effects`() = runBlockingTest {
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

        store.toList() shouldContainExactly listOf("", "1", "16", "167", "1678", "16789", "167892", "1678926", "16789267", "167892678", "1678926789")
        sideEffect1Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
        sideEffect2Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
    }

    @Test
    @Ignore
    fun `store with 2 side effects which react to side effect actions`() = runBlockingTest {
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

        store.toList() shouldContainExactly listOf("", "1", "16", "167", "1678", "16789", "167892", "1678926", "16789267", "167892678", "1678926789")
        sideEffect1Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
        sideEffect2Actions shouldContainExactly listOf(1, 6, 7, 8, 9, 2, 6, 7, 8, 9)
    }

    private suspend fun <T> Flow<T>.toList() = withTimeout(1000L) {
        toCollection(mutableListOf())
    }
}
