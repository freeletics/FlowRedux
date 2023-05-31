package com.freeletics.flowredux

import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import com.freeletics.flowredux.dsl.FlowReduxStoreBuilder
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
    specBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit = {},
) : FlowReduxStateMachine<TestState, TestAction>(initialStateSupplier = { initialState }) {

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
