package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed class WorkoutState {
    data class CountdownState(val timeLeft: Long) : WorkoutState()
}

sealed class WorkoutAction

object JvmLogger : FlowReduxLogger {
    override fun log(message: String) {
        println(message)
    }
}

class RealStopWatch(private val delayMilliseconds: Long = 1000) {

    fun timeElapsed(): Flow<Long> = flow {
        coroutineScope {
            var currentNumber = 0L
            while (isActive) {
                println("in loop")
                delay(delayMilliseconds)
                currentNumber++
                emit(currentNumber)
            }
            println("Exit loop")
        }
    }
}


fun main() = runBlocking {
    val sm = TestStateMachine()
    launch {
        sm.state.collect {
            println("NewState: $it")
        }
    }
    Unit
}


class TestStateMachine() :
    FlowReduxStateMachine<WorkoutState, WorkoutAction>(JvmLogger, WorkoutState.CountdownState(5)) {
    private val stopWatch = RealStopWatch()

    init {
        spec {
            inState<WorkoutState.CountdownState> {
                observeWhileInState(stopWatch.timeElapsed()) { value, _, setState ->
                    println("received $value")
                    setState {
                        println("setState $value")
                        if (it is WorkoutState.CountdownState) {
                            WorkoutState.CountdownState(it.timeLeft - 1)
                        } else it
                    }
                }
            }
        }
    }
}