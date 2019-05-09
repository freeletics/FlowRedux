package com.freeletics.flowredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

@FlowPreview
@UseExperimental(ExperimentalCoroutinesApi::class)
fun <A, S> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
    reducer: Reducer<S, A>
): Flow<S> = flow {

    var currentState: S = initialStateSupplier()
    val mutex = Mutex()
    val stateAccessor: StateAccessor<S> = { currentState }

    println("Emitting initial state")

    // Emit the initial state
    emit(currentState)

    coroutineScope {

        val actionBroadcastChannel: BroadcastChannel<A> = BroadcastChannel(100)
        val actionBroadcastChannelAsFlow: Flow<A> = actionBroadcastChannel.asFlow()

        for (sideEffect in sideEffects) {
            launch {
                println("Subscribing sideeffect")
                sideEffect(actionBroadcastChannelAsFlow, stateAccessor).collect { action: A ->
                    println("Received action $action from sideeffect")

                    // Change state
                    mutex.lock()
                    val newState: S = reducer(currentState, action)
                    println("Reduce from sideeffect: $currentState with $action -> $newState")
                    currentState = newState
                    mutex.unlock()
                    emit(newState)

                    // broadcast action
                    actionBroadcastChannel.send(action)
                }
                println("Completed sideeffect")
            }
        }

        println("Subscribing upstream")
        collect { action: A ->
            println("Received action $action from upstream")

            // Change State
            mutex.lock()
            val newState: S = reducer(currentState, action)
            println("Reduce from upstream: $currentState with $action -> $newState")
            currentState = newState
            mutex.unlock()
            emit(newState)

            // React on actions from upstream by broadcasting Actions to SideEffects
            actionBroadcastChannel.send(action)
        }
        println("Completed upstream")

        actionBroadcastChannel.cancel()
    }

}

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
