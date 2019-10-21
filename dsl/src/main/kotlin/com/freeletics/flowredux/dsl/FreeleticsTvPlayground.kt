package com.freeletics.flowredux.dsl

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed class FState {

    data class StartCountDownState(val timeLeft: Int) : FState()
    class WorkoutInProfress() : FState()
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
                            FState.WorkoutInProfress()
                    }
                    else -> state
                }

            }
        }

    }

    inState<FState.WorkoutInProfress> {
        on<FAction.ClickOnScreenAction> { _, _, setState ->

            setState {
                FState.StartCountDownState(5)
            }
        }

    }
})

fun main() = runBlocking {

    val sm = WorkoutStateMachine()

    launch {
        delay(8000)
        sm.dispatch(FAction.ClickOnScreenAction)
        delay(8000)
        sm.dispatch(FAction.ClickOnScreenAction)
    }

    sm.state.collect { println(it) }
}