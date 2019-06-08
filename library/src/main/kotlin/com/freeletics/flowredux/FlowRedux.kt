package com.freeletics.flowredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@ExperimentalCoroutinesApi
@FlowPreview
fun <A, S> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
    reducer: Reducer<S, A>
): Flow<S> = flow {

    var currentState: S = initialStateSupplier()
    val mutex = Mutex()
    val stateAccessor: StateAccessor<S> = { currentState }
    val loopback: BroadcastChannel<A> = BroadcastChannel(100)

    // Emit the initial state
    println("Emitting initial state")
    emit(currentState)

    suspend fun callReducer(origin: String, action: A) {
        println("$origin: action $action received")

        // Change state
        mutex.lock()
        val newState: S = reducer(currentState, action)
        println("$origin: reducing $currentState with $action -> $newState")
        currentState = newState
        mutex.unlock()
        emit(newState)

        // broadcast action
        loopback.send(action)
    }

    coroutineScope {
        val loopbackFlow = loopback.asFlow()
        sideEffects.forEachIndexed { index, sideEffect ->
            launch {
                println("Subscribing to SideEffect$index")
                sideEffect(loopbackFlow, stateAccessor).collect { callReducer("SideEffect$index", it) }
                println("Completed SideEffect$index")
            }
        }

        println("Subscribing to upstream")
        collect { callReducer("Upstream", it) }
        println("Completed upstream")
        loopback.cancel()
        println("Cancelled loopback")
    }
}
