package com.freeletics.flowredux.dsl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.IllegalArgumentException

sealed class FState {

    data class StartCountDownState(val timeLeft: Int) : FState()
    class WorkoutInProgress() : FState()
}

sealed class FAction {
    object ClickOnScreenAction : FAction()
}

class WorkoutStateMachine : FlowReduxStateMachine<FState, FAction>(FState.StartCountDownState(5), {

    inState<FState.StartCountDownState> {

        observeWhileInState(interval()) { _, _, setState ->

            setState { state ->
                when (state) {
                    is FState.StartCountDownState -> {
                        val timeLeft = state.timeLeft - 1
                        if (timeLeft > 0)
                            FState.StartCountDownState(timeLeft)
                        else
                            FState.WorkoutInProgress()
                    }
                    else -> state
                }

            }
        }

    }

    inState<FState.WorkoutInProgress> {
        on<FAction.ClickOnScreenAction> { _, _, setState ->

            setState {
                FState.StartCountDownState(5)
            }
        }

    }
})







fun main() = runBlocking {

    val sm = WorkoutStateMachine()

    launch(Dispatchers.IO) {
        while (isActive) {
           readCommandLineAndDispatchActions(sm)
        }
    }

    sm.state.collect { println(it) }
}












suspend fun readCommandLineAndDispatchActions(sm : WorkoutStateMachine){
    val input = readLine()
    try {

        val action: FAction = when (input) {
            "c" -> FAction.ClickOnScreenAction
            else -> {
                println("unknown command")
                throw IllegalArgumentException()
            }

        }
        sm.dispatch(action)
    } catch (t: IllegalArgumentException) {
    }
}