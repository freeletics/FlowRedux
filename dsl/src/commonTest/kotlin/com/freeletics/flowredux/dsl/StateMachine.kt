package com.freeletics.flowredux.dsl

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun StateMachine(
        builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): FlowReduxStateMachine<TestState, TestAction> {
    val sm = object : FlowReduxStateMachine<TestState, TestAction>(
        TestState.Initial,
        CoroutineScope(Dispatchers.Default),
        CommandLineLogger
    ){
        init {
            spec(builderBlock)
        }
    }

    return sm
}
