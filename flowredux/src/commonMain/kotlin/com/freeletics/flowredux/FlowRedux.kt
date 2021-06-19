package com.freeletics.flowredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Creates a Redux store with an initial state. If your initial state is an expensive computation
 * consider using [reduxStore]  which has an initialStateSupplier as parameter that produces the
 * first state lazily once the flow starts.

@ExperimentalCoroutinesApi
fun <A, S> Flow<A>.reduxStore(
        initialState: S,
        sideEffects: Iterable<SideEffect<S, A>>,
        logger: FlowReduxLogger? = null,
        reducer: Reducer<S, A>
): Flow<S> = reduxStore(
        initialStateSupplier = { initialState },
        sideEffects = sideEffects,
        logger = logger,
        reducer = reducer
)
 */

/**
 * Creates a Redux store with a [initialStateSupplier] that produces the first state lazily once
 * the flow starts.
 */
@ExperimentalCoroutinesApi
fun <A, S> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
    logger: FlowReduxLogger? = null,
    reducer: Reducer<S, A>
): Flow<S> = flow {

    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }
    val loopback: MutableSharedFlow<A> = MutableSharedFlow(extraBufferCapacity = 1)

    // Emit the initial state
    logger?.log("Emitting initial state $currentState")
    emit(currentState)

    val upstreamActions = this@reduxStore
    val sideEffectActions = sideEffects.map { it(loopback, getState) }

    (sideEffectActions + upstreamActions).merge().collect { action ->
        logger?.log("action $action received")

        // Change state
        val newState: S = reducer(currentState, action)
        logger?.log("reducing $currentState with $action -> $newState")
        currentState = newState
        emit(newState)

        // broadcast action
        loopback.emit(action)
    }
}
