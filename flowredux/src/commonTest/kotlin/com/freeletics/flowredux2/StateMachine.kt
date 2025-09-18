package com.freeletics.flowredux2

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.stateMachine(
    initialState: TestState = TestState.Initial,
    specBlock: FlowReduxBuilder<TestState, TestAction>.() -> Unit = {},
): FlowReduxStateMachine<SharedFlow<TestState>, TestAction> {
    return StateMachineFactory(initialState, specBlock).shareIn(backgroundScope)
}

@OptIn(DelicateCoroutinesApi::class)
internal fun FlowReduxStateMachine<*, TestAction>.dispatchAsync(action: TestAction) {
    GlobalScope.launch {
        dispatch(action)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class StateMachineFactory(
    initialState: TestState = TestState.Initial,
    specBlock: FlowReduxBuilder<TestState, TestAction>.() -> Unit = {},
) : FlowReduxStateMachineFactory<TestState, TestAction>() {
    init {
        initializeWith(reuseLastEmittedStateOnLaunch = false) { initialState }
        spec(specBlock)
    }

    override val actionChannelCapacity: Int = Channel.RENDEZVOUS
}
