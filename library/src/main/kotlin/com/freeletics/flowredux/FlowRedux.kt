package com.freeletics.flowredux

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

fun <A, S> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
    reducer: Reducer<S, A>
): Flow<S> = flow {

    var currentState: S = initialStateSupplier()
    val mutex: Mutex = Mutex()
    val stateAccessor: StateAccessor<S> = { currentState }

    println("Emitting initial state")

    // Emit the initial state
    emit(currentState)

    coroutineScope {

        val actionBroadcastChannel: BroadcastChannel<A> = BroadcastChannel(1)
        val actionBroadcastChannelAsFlow: Flow<A> = actionBroadcastChannel.asFlow()

        launch {

            for (sideEffect in sideEffects) {
                println("Subscribing sideeffect")

                sideEffect(actionBroadcastChannelAsFlow, stateAccessor).collect { action: A ->
                    // change state
                    println("Got action $action from sideeffect")
                    mutex.lock()
                    val newState: S = reducer(currentState, action)
                    currentState = newState
                    mutex.unlock()
                    emit(newState)

                    // broadcast action
                    actionBroadcastChannel.send(action)
                }
            }
        }

        collect { action: A ->
            println("Received Action $action from upstream")

            // Change State
            mutex.lock()
            val newState: S = reducer(currentState, action)
            currentState = newState
            mutex.unlock()
            emit(newState)

            // React on actions from upstream by broadcasting Actions to SideEffects
            actionBroadcastChannel.send(action)
        }
    }

}

fun main() = runBlocking {
    val sideEffect1: SideEffect<String, Int> = { actions: Flow<Int>, stateAccessor: StateAccessor<String> ->
        actions.flatMap { action ->
            println("-- SF1: Got action $action . current state ${stateAccessor()}")
            if (action != 3)
                flowOf(3)
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
            sideEffects = listOf(sideEffect1)
        ) { state, action ->
            state + action
        }
        .collect {
            println("STATE: $it")
        }

}
