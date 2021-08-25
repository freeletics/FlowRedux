package com.freeletics.flowredux.dsl

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class SubStateMachineTest {

    @Test
    fun `delegate to sub statemachine while in state`() = suspendTest {
        val child = ChildStateMachine(this) {

            inState<TestState.Initial> {
                on<TestAction.A1> { _, _ ->
                    OverrideState(TestState.S1)
                }
            }
        }

        val parent = StateMachine {
            inState<TestState.Initial> {

                stateMachine(
                    stateMachine = child,
                    actionMapper = { it }) { _, subStateMachineState ->
                    OverrideState(subStateMachineState)
                }

            }
        }

        parent.state.test {
            assertEquals(TestState.Initial, awaitItem())
            parent.dispatch(TestAction.A1)
            assertEquals(TestState.S1, awaitItem())
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
private fun ChildStateMachine(
    coroutineScope: CoroutineScope,
    builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): FlowReduxStateMachine<TestState, TestAction> {
    return object : FlowReduxStateMachine<TestState, TestAction>(
        TestState.Initial,
        coroutineScope,
        CommandLineLogger
    ) {

        init {
            spec(builderBlock)
        }
    }
}