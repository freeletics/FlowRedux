package com.freeletics.flowredux.dsl

import org.junit.Assert.assertEquals
import org.junit.Test

class ReducerTest {

    private sealed class TestState {
        object A : TestState()
        object B : TestState()
    }

    @Test
    fun `run SelfReducableAction on returning True`() {
        val action =
            SelfReducableAction<TestState, Any>(
                loggingInfo = "info",
                reduce = { TestState.B },
                runReduceOnlyIf = { true }
            )

        val expected = TestState.B
        val actual = reducer(
            TestState.A,
            action
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `dont run SelfReducableAction on returning False`() {
        val action =
            SelfReducableAction<TestState, Any>(
                loggingInfo = "info",
                reduce = { TestState.B },
                runReduceOnlyIf = { false }
            )

        val expected = TestState.A
        val actual = reducer(
            TestState.A,
            action
        )

        assertEquals(expected, actual)
    }
}