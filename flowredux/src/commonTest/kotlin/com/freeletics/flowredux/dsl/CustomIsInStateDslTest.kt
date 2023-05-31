package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitComplete
import app.cash.turbine.test
import com.freeletics.flowredux.StateMachine
import com.freeletics.flowredux.TestAction
import com.freeletics.flowredux.TestState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class CustomIsInStateDslTest {

    @Test
    fun onActionTriggersOnlyWhileInCustomState() = runTest {
        var counter1 = 0
        var counter2 = 0

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("foo", 2)

        val signal = Channel<Unit>()

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, state ->
                    state.override { gs1 }
                }
            }
            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                on<TestAction.A1> { _, state ->
                    counter1++
                    state.override { gs2 }
                }
            }

            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                on<TestAction.A1> { _, state ->
                    signal.awaitComplete()
                    counter2++
                    state.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs2, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            signal.close()
            assertEquals(TestState.S1, awaitItem())
        }

        assertEquals(1, counter1)
        assertEquals(1, counter2)
    }

    @Test
    fun collectWhileInStateStopsWhenLeavingCustomState() = runTest {
        var reached = false

        val gs1 = TestState.GenericState("asd", 1)
        val gs2 = TestState.GenericState("2", 2)

        val signal1 = Channel<Unit>()
        val signal2 = Channel<Unit>()

        val sm = StateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> { _, state ->
                    state.override { gs1 }
                }
            }
            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 1 }) {
                collectWhileInState(
                    flow {
                        emit(2)
                        signal1.awaitComplete()
                        reached = true
                        fail("This should never be reached")
                    },
                ) { value, state ->
                    state.override { TestState.GenericState(value.toString(), value) }
                }
            }

            inStateWithCondition(isInState = { it is TestState.GenericState && it.anInt == 2 }) {
                onEnter {
                    signal2.awaitComplete()
                    return@onEnter it.override { TestState.S1 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            assertEquals(gs2, awaitItem())
            signal1.close()
            signal2.close()
            assertEquals(TestState.S1, awaitItem())
        }

        assertFalse(reached)
    }
}
