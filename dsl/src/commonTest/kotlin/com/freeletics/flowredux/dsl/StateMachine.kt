package com.freeletics.flowredux.dsl

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StateMachine constructor(
    initialState : TestState = TestState.Initial,
    specBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
) : FlowReduxStateMachine<TestState, TestAction>(
    logger = CommandLineLogger,
    initialStateSupplier = { initialState }
) {

    init {
        spec(specBlock)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun dispatchAsync(action: TestAction) {
        GlobalScope.launch {
            dispatch(action)
        }
    }
}
