package com.freeletics.flowredux.dsl

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.awt.MediaTracker.LOADING
import kotlin.random.Random

sealed class State {
    object S1 : State() {
        override fun toString(): String = "S1"
    }

    data class S2(val value: Int) : State()

    object S3 : State() {
        override fun toString(): String = "S3"
    }
}

sealed class MyAction {
    object Action1 : MyAction() {
        override fun toString(): String = "Action1"
    }

    object Action2 : MyAction() {
        override fun toString(): String = "Action2"
    }
}

val sm = flow<MyAction> {
    emit(MyAction.Action1)
    emit(MyAction.Action2)
}.reduxStore<State, MyAction>(State.S1) {

    inState<State.S1> {

     /*   on<MyAction.Action1> and S1 { getState, setState, action: MyAction.Action1 ->

            /*
            inState<INITIAL> {
                setState { LOAODING }
                http()
                setState<LOADING> {
                    RESULT
                }
            }


            // MvRx setState? some special checks?
            setState { // can it be S1? instead of State?
                State.S2(value = 1)
            }

            val res = http()

            setState {

            }
            *
             */
        }

      */
    }


    inState<State.S2> {

        on<MyAction.Action2>(block = ::onAction2)

        observe(interval()) { value, getState, setState ->
            setState { currentState ->
                when (currentState) {
                    is State.S2 -> State.S2(currentState.value + 1)
                    else -> currentState
                }
            }
        }

    }

}

suspend fun onAction2(
    getState: () -> State,
    setState: SetState<State>,
    action: MyAction.Action2
) {

    //delay(3000) // Could be an http request ... but you could also lunch a new flow here
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

fun interval(initialDelay: Long = 0) = flow<Long> {

    var counter = 0L

    coroutineScope {
        if (initialDelay > 0) {
            delay(initialDelay)
        }

        while (isActive) {
            counter++
            delay(1000)
            emit(counter)
        }
    }
}
