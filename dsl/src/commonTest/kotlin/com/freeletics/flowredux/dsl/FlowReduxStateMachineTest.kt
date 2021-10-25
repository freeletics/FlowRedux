package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlin.time.ExperimentalTime

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

    @Test
    fun `state flow is cold by starting on initial state and dispatches to latest Flow only`() =
        suspendTest {

            val sm = StateMachine {
                inState<TestState.Initial> {
                    on<TestAction.A1> { _, _ -> OverrideState(TestState.S1) }
                }
                inState<TestState.S1> {
                    on<TestAction.A1> { _, _ -> OverrideState(TestState.S2) }
                }
            }

            val job1 = launch {
                val flow1 = sm.state
                flow1.test {
                    assertEquals(TestState.Initial, awaitItem())
                    sm.dispatch(TestAction.A1)
                    assertEquals(TestState.S1, awaitItem())
                    delay(50) // wait until flow2 did some work, see below
                    expectNoEvents()
                }
            }

            val job2 = launch {
                delay(30) // ensure that job1 starts first
                val flow2 = sm.state

                flow2.test {
                    // although flow1 is in TestState S1,
                    // the "cold" flow2 starts in initial state again
                    assertEquals(TestState.Initial, awaitItem())

                    sm.dispatch(TestAction.A1) // dispatching A1 only impacts flow2
                    assertEquals(TestState.S1, awaitItem())
                }

            }

            job1.join()
            job2.join()
        }
}
