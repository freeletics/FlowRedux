package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FlowReduxStateMachineTest {

    @Test
    fun `empty state machine just emits initial state`() = runTest {
        val sm = StateMachine { }
        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
        }
    }

    @Test
    fun `calling spec block twice throws exception`() {

        val sm = object : FlowReduxStateMachine<Any, Any>(Any()) {

            init {
                spec { }
            }

            fun specAgain() {
                spec { }
            }
        }

        try {
            sm.specAgain()
            fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                "State machine spec has already been set. It's only allowed to call spec {...} once."
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun `no spec block set throws exception`() {

        val sm = object : FlowReduxStateMachine<Any, Any>(Any()) {}

        try {
            sm.state
            fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                "No state machine specs are defined. Did you call spec { ... } in init {...}?\n" +
                    "Example usage:\n" +
                    "\n" +
                    "class MyStateMachine : FlowReduxStateMachine<State, Action>(InitialState) {\n" +
                    "\n" +
                    "    init{\n" +
                    "        spec {\n" +
                    "            inState<FooState> {\n" +
                    "                on<BarAction> { ... }\n" +
                    "            }\n" +
                    "            ...\n" +
                    "        }\n" +
                    "    }\n" +
                    "}"
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun `dispatching without any state flow collector throws exception`() = runTest {
        val sm = StateMachine {}

        val action = TestAction.A1
        val exception = assertFailsWith<IllegalStateException> {
            sm.dispatch(action)
        }

        val expectedMsg =
            "Cannot dispatch action $action because state Flow of this " +
                "FlowReduxStateMachine is not collected yet. Start collecting the state " +
                "Flow before dispatching any action."

        assertEquals(expectedMsg, exception.message)
    }

    @Test
    fun `observing state multiple times in parallel throws exception`() = runTest {
        val sm = StateMachine {}

        var collectionStarted = false
        val job = launch {
            sm.state.collect {
                collectionStarted = true
            }
        }

        while (!collectionStarted) {
            delay(1)
        }

        val exception = assertFailsWith<IllegalStateException> {
            sm.state.collect { }
        }

        val expectedMsg =
            "Can not collect state more than once at the same time. Make sure the" +
                "previous collection is cancelled before starting a new one. " +
                "Collecting state in parallel would lead to subtle bugs."

        assertEquals(expectedMsg, exception.message)

        job.cancel()
    }

    @Test
    fun `observing state multiple times in sequence`() = runTest {
        val sm = StateMachine {}

        // each call will collect the first item and then stop collecting
        sm.state.first()
        sm.state.first()
    }
}
