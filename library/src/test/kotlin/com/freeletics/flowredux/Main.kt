package com.freeletics.flowredux

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

@UseExperimental(FlowPreview::class)
fun main() = runBlocking {
    val sideEffect1: SideEffect<String, Int> = { actions, stateAccessor ->
        actions.flatMapConcat { action ->
            println("-- SF1: Got action $action . current state ${stateAccessor()}")
            if (action < 3)
                flowOf(3)
            else
                emptyFlow()
        }
    }
    val sideEffect2: SideEffect<String, Int> = { actions, stateAccessor ->
        actions.flatMapConcat { action ->
            println("-- SF2: Got action $action . current state ${stateAccessor()}")
            if (action < 3)
                flowOf(4)
            else if (action < 4)
                flowOf(5)
            else
                emptyFlow()
        }
    }


    flow {
        delay(1000)
        emit(1)
        delay(4000)
        emit(2)
    }
        //.delayFlow(1000)
        .reduxStore(
            initialStateSupplier = { "Start" },
            sideEffects = listOf(sideEffect1, sideEffect2)
        ) { state, action ->
            state + action
        }
        .collect {
            println("STATE: $it")
        }

}
