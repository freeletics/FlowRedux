package com.freeletics.flowredux.dsl

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun StateMachine(
    builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): TestStateMachine {
    return TestStateMachine(builderBlock)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TestStateMachine constructor(
    specBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
) : FlowReduxStateMachine<TestState, TestAction>(
    logger = CommandLineLogger,
    initialState = TestState.Initial
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
