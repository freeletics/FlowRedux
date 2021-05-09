package com.freeletics.flowredux.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

expect fun suspendTest(body: suspend CoroutineScope.() -> Unit)

fun dispatchAsync(sm: FlowReduxStateMachine<TestState, TestAction>, action: TestAction) {
    GlobalScope.launch {
        sm.dispatch(action)
    }
}