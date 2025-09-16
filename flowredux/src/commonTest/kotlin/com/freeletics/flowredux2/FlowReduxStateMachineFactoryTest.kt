package com.freeletics.flowredux2

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class FlowReduxStateMachineFactoryTest {
    @Test
    fun emptyStateMachineJustEmitsInitialState() = runTest {
        val sm = stateMachine { }
        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
        }
    }

    @Test
    fun noInitializeWithBlockSetThrowsException() = runTest {
        val sm = object : FlowReduxStateMachineFactory<Any, Any>() {
            init {
                spec { }
            }
        }

        try {
            sm.launchIn(backgroundScope)
            fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                """
                No initial state for the state machine was specified, did you call one of the initializeWith()
                methods?

                Example usage:
                class MyStateMachine : FlowReduxStateMachineFactory<State, Action>() {
                    init{
                        initializeWith(InitialState)
                        spec {
                            ...
                        }
                    }
                }
                """.trimIndent()
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun callingSpecBlockTwiceThrowsException() {
        val sm = object : FlowReduxStateMachineFactory<Any, Any>() {
            init {
                initializeWith { Any() }
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
    fun noSpecBlockSetThrowsException() = runTest {
        val sm = object : FlowReduxStateMachineFactory<Any, Any>() {
            init {
                initializeWith { Any() }
            }
        }

        try {
            sm.launchIn(backgroundScope)
            fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                """
                No state machine specs are defined. Did you call spec { ... } in init {...}?
                Example usage:

                class MyStateMachine : FlowReduxStateMachineFactory<State, Action>() {

                    init{
                        initializeWith(...)
                        spec {
                            inState<FooState> {
                                on<BarAction> { ... }
                            }
                            ...
                        }
                    }
                }
                """.trimIndent()
            assertEquals(expected, e.message)
        }
    }
}
