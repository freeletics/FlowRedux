package com.freeletics.flowredux.dsl

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

sealed class State {
    object S1 : State() {
        override fun toString(): String ="S1"
    }
    data class S2(val value: Int) : State()
}

sealed class MyAction {
    object Action1 : MyAction() {
        override fun toString(): String = "Action1"
    }
    object Action2 : MyAction(){
        override fun toString(): String = "Action2"
    }
}

val sm = flow<MyAction> {
    emit(MyAction.Action1)
    emit(MyAction.Action2)
}.reduxStoreDsl<State, MyAction>(State.S1) {

    inState<State.S1> {

        on<MyAction.Action1> { getState, setState, action: MyAction.Action1 ->
            setState {
                State.S2(value = 1)
            }
        }
    }


    inState<State.S2> {
        // TODO implement an example
        on<MyAction.Action2>(block = ::onAction2)

    }

}

suspend fun onAction2(
    getState: () -> State,
    setState: SetState<State>,
    action: MyAction.Action2
) {

    delay(100) // Could be an http request ... but you could also lunch a new flow here
    val value = Random.nextInt()

    setState {
        State.S2(value)
    }
}

fun main() = runBlocking {
    sm.collect {
        println(it)
    }
}