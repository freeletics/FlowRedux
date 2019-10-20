package com.freeletics.flowredux.dsl

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.MediaTracker.LOADING
import kotlin.concurrent.timer

sealed class FState {

    data class StartCountDownState(val timeLeft: Int) : FState()
    class LäuftState() : FState()
}

sealed class FAction {
    object ClickOnScreenAction : FAction()
}

class WorkoutStateMachine : FlowReduxStateMachine<FState, FAction>(FState.StartCountDownState(5), {

    inState<FState.StartCountDownState> {

        observe(interval()) { value, _, setState ->
            setState { state ->
                when (state) {
                    is FState.StartCountDownState -> {
                        val timeLeft = state.timeLeft - 1
                        if (timeLeft > 0)
                            FState.StartCountDownState(timeLeft)
                        else
                            FState.LäuftState()
                    }
                    else -> state
                }

            }
        }

    }
})

fun main() = runBlocking {

    val sm = WorkoutStateMachine()


    launch {
        delay(8000)
        sm.dispatch(FAction.ClickOnScreenAction)
    }

    sm.state.collect { println(it) }
}