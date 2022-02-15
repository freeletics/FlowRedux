package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class FlowReduxStateMachineTest {

    @Test
    fun `empty statemachine just emits initial state`() = suspendTest {
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
    fun `dispatching without any state flow collector throws exception`() = suspendTest {
        val sm = StateMachine {}

        val exception = assertFailsWith<IllegalStateException> {
            sm.dispatch(TestAction.A1)
        }

        val expectedMsg =
            "Cannot dispatch action ${TestAction.A1} because state Flow of this " +
                    "FlowReduxStateMachine is not collected yet. Start collecting the state " +
                    "Flow before dispatching any action."

        assertEquals(expectedMsg, exception.message)
    }
}
