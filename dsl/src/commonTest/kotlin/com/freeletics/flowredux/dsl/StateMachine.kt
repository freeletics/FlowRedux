package com.freeletics.flowredux.dsl

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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


fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.dispatchAsync(action: A) {
    val sm = this
    GlobalScope.launch {
        sm.dispatch(action)
    }
}