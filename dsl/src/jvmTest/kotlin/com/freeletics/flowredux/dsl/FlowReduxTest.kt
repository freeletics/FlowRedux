package com.freeletics.flowredux.dsl

import org.junit.Assert
import org.junit.Test

class FlowReduxTest {

    @Test
    fun callingSpecTwiceThrowsException() {

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
            Assert.fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                "State machine spec has already been set. It's only allowed to call spec {...} once."
            Assert.assertEquals(expected, e.message)
        }
    }

    @Test
    fun noSpecSetThrowsException() {

        val sm = object : FlowReduxStateMachine<Any, Any>(Any()) {}

        try {
            sm.state
            Assert.fail("Exception expected to be thrown")
        } catch (e: IllegalStateException) {
            val expected =
                "No state machine specs are defined. Did you call spec { ... } in init {...}?\n" +
                    "Example usage:\n" +
                    "\n" +
                    "class MyStateMachine : FlowReduxStateMachine<State, Action>(InitialState) {\n" +
                    "    \n" +
                    "    init{\n" +
                    "        spec {\n" +
                    "            inState<FooState> {\n" +
                    "                on<BarAction> { ... }\n" +
                    "            }\n" +
                    "            ...\n" +
                    "        }\n" +
                    "    }\n" +
                    "}"
            Assert.assertEquals(expected, e.message)
        }
    }
}