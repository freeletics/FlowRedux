package com.freeletics.flowredux.dsl

fun StateMachine(
        builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): FlowReduxStateMachine<TestState, TestAction> {
    val sm: FlowReduxStateMachine<TestState, TestAction> = object : FlowReduxStateMachine<TestState, TestAction>(
            CommandLineLogger,
            TestState.Initial){

        init {
            spec(builderBlock)
        }
    }

    return sm
}