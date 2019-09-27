package com.freeletics.flowredux.dsl

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

sealed class State {
    object S1 : State()
    data class S2(val value: Int) : State()
}

sealed class MyAction {
    object Action1 : MyAction()
    object Action2 : MyAction()
}

val sm = flow<MyAction> {
    emit(MyAction.Action1)
}.reduxStoreDsl<State, MyAction>(State.S1) {

    inState<State.S1> {

        on<MyAction.Action1> { getState, setState, action: MyAction.Action1 ->

        }

        on<MyAction.Action2>(block = ::onAction2)
    }


    inState<State.S2> {
        // TODO implement an example
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