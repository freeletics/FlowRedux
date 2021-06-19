package com.freeletics.flowredux.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

expect fun suspendTest(body: suspend CoroutineScope.() -> Unit)

@OptIn(DelicateCoroutinesApi::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
fun dispatchAsync(sm: FlowReduxStateMachine<TestState, TestAction>, action: TestAction) {
    GlobalScope.launch {
        sm.dispatch(action)
    }
}
