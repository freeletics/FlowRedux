package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.StateMachine
import com.freeletics.flowredux.TestAction
import com.freeletics.flowredux.TestState
import com.freeletics.flowredux.util.CoroutineWaiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
internal class SubStateMachinesMapTest {

    @Test
    fun addingNewStateMachineWorksAndCancelsPreviousOne() = runTest {
        val map = OnActionStartStateMachine.SubStateMachinesMap<TestState, TestAction, TestAction.A4>()

        val actionThatTriggered = TestAction.A4(1)
        val w1 = CoroutineWaiter()
        val s1 = StateMachine()
        assertEquals(0, s1.stateFlowStarted)
        assertEquals(0, s1.stateFlowCompleted)
        val b1 = AwaitableBoolean { s1.stateFlowStarted >= 1 }
        val j1 = launch {
            s1.state.collect()
        }

        b1.awaitTrue()
        assertEquals(0, map.size())

        map.cancelPreviousAndAddNew(actionThatTriggered, s1, w1, j1)
        assertEquals(1, map.size())

        val s2 = StateMachine()
        val w2 = CoroutineWaiter()
        val j2 = launch {
            s2.state.collect()
        }

        val b2 = AwaitableBoolean { s1.stateFlowCompleted >= 1 }
        map.cancelPreviousAndAddNew(actionThatTriggered, s2, w2, j2)
        assertEquals(1, map.size())
        assertTrue(j1.isCancelled)
        assertEquals(1, s1.stateFlowStarted)
        b2.awaitTrue()
        assertEquals(1, s1.stateFlowCompleted)

        j2.cancel() // Needed, otherwise runTest() will wait for all child jobs to complete
    }

    @Test
    fun addingStateMachineDoesNotCancelPreviousIfActionThatTriggeredIsNotEqualToOriginalOne() = runTest {
        val map = OnActionStartStateMachine.SubStateMachinesMap<TestState, TestAction, TestAction.A4>()

        val a1 = TestAction.A4(1)
        val s1 = StateMachine()
        val w1 = CoroutineWaiter()
        assertEquals(0, s1.stateFlowStarted)
        assertEquals(0, s1.stateFlowCompleted)
        val b1 = AwaitableBoolean { s1.stateFlowStarted >= 1 }
        val j1 = launch {
            s1.state.collect()
        }

        b1.awaitTrue()
        assertEquals(0, map.size())

        map.cancelPreviousAndAddNew(a1, s1, w1, j1)
        assertEquals(1, map.size())

        val s2 = StateMachine()
        val w2 = CoroutineWaiter()
        val b2 = AwaitableBoolean { s2.stateFlowStarted >= 1 }

        val j2 = launch {
            s2.state.collect()
        }

        val a2 = TestAction.A4(2) // Different action
        map.cancelPreviousAndAddNew(a2, s2, w2, j2)
        b2.awaitTrue()
        assertEquals(2, map.size())

        // check s1 is still running after cancelPreviousAndAddNew()
        assertTrue(j1.isActive)
        assertEquals(1, s1.stateFlowStarted)
        assertEquals(0, s1.stateFlowCompleted)

        // check s2 is still running after cancelPreviousAndAddNew()
        assertTrue(j2.isActive)
        assertEquals(1, s2.stateFlowStarted)
        assertEquals(0, s2.stateFlowCompleted)

        // Needed, otherwise runTest() will wait for all child jobs to complete
        j1.cancel()
        j2.cancel()
    }

    @Test
    fun iteratingOverAllStateMachinesWork() = runTest {
        val map = OnActionStartStateMachine.SubStateMachinesMap<TestState, TestAction, TestAction.A4>()
        val a1 = TestAction.A4(1)
        val w1 = CoroutineWaiter()
        val s1 = StateMachine()
        val j1 = launch {
            s1.state.collect()
        }

        assertEquals(0, map.size())

        map.cancelPreviousAndAddNew(a1, s1, w1, j1)
        assertEquals(1, map.size())

        val a2 = TestAction.A4(2) // Different action
        val w2 = CoroutineWaiter()
        val s2 = StateMachine()
        val j2 = launch {
            s2.state.collect()
        }

        map.cancelPreviousAndAddNew(a2, s2, w2, j2)
        assertEquals(2, map.size())

        var i = 0
        map.forEachStateMachine { sm, waiter ->
            when (i) {
                0 -> {
                    assertSame(s1, sm)
                    assertTrue(w1.isTheSame(waiter))
                }

                1 -> {
                    assertSame(s2, sm)
                    assertTrue(w2.isTheSame(waiter))
                }
                else -> throw Exception("Unexpected loop value $i")
            }
            i++
        }

        assertEquals(2, i)

        // Needed, otherwise runTest() will wait for all child jobs to complete
        j1.cancel()
        j2.cancel()
    }
}

// One day we could replace this with channels etc. to make it more efficient.
// For now it does the job.
private class AwaitableBoolean(
    private var value: () -> Boolean,
) {

    suspend fun awaitTrue(timeOutMillis: Long = 200) {
        withTimeout(timeOutMillis) {
            while (!value()) {
                delay(2)
            }
        }
    }

    fun set(value: Boolean) {
        this.value = { value }
    }
}
