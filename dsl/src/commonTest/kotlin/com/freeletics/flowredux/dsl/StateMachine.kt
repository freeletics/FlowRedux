package com.freeletics.flowredux.dsl

fun StateMachine(
        builderBlock: FlowReduxStoreBuilder<State, Action>.() -> Unit
): FlowReduxStateMachine<State, Action> {
    val sm: FlowReduxStateMachine<State, Action> = object : FlowReduxStateMachine<State, Action>(
            CommandLineLogger,
            State.Initial){

        init {
            spec(builderBlock)
        }
    }

    return sm
}

/*
class StateMachine(
    builderBlock: FlowReduxStoreBuilder<State, Action>.() -> Unit
) : FlowReduxStateMachine<State, Action>(
    CommandLineLogger,
    State.Initial
) {

    init {
        spec(builderBlock)
    }
}

 */