package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

sealed class Action {
    object A1 : Action()
    object A2 : Action()
}

sealed class State {
    object Initial : State()
    object S1 : State()
    object S2 : State()
    object S3 : State()

    data class GenericState(val aString : String, val anInt : Int) : State()
}

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

object CommandLineLogger : FlowReduxLogger {
    override fun log(message: String) {
        println(message)
    }
}
