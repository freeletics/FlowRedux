package com.freeletics.flowredux2

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class StateMachine(
    initialState: TestState = TestState.Initial,
    specBlock: FlowReduxBuilder<TestState, TestAction>.() -> Unit = {},
) : LegacyFlowReduxStateMachine<TestState, TestAction>(initialStateSupplier = { initialState }) {
    init {
        spec(specBlock)
    }

    var stateFlowCompleted = 0
    var stateFlowStarted = 0

    override val state: Flow<TestState> = super.state
        .onStart { stateFlowStarted++ }
        .onCompletion { stateFlowCompleted++ }

    @OptIn(DelicateCoroutinesApi::class)
    fun dispatchAsync(action: TestAction) {
        GlobalScope.launch {
            dispatch(action)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class StateMachineFactory(
    initialState: TestState = TestState.Initial,
    specBlock: FlowReduxBuilder<TestState, TestAction>.() -> Unit = {},
) : FlowReduxStateMachineFactory<TestState, TestAction>() {
    init {
        initializeWith(initialState, reuseLastEmittedStateOnLaunch = false)
        spec(specBlock)
    }
}
