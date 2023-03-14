package com.freeletics.flowredux

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
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
internal fun <A, S> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
    reducer: Reducer<S, A>,
): Flow<S> = flow {
    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }

    // Emit the initial state
    emit(currentState)

    val loopbacks = sideEffects.map {
        Channel<A>()
    }
    val sideEffectActions = sideEffects.mapIndexed { index, sideEffect ->
        val actionsFlow = loopbacks[index].consumeAsFlow()
        sideEffect(actionsFlow, getState)
    }
    val upstreamActions = this@reduxStore

    (sideEffectActions + upstreamActions).merge().collect { action ->
        // Change state
        val newState: S = reducer(currentState, action)
        currentState = newState
        emit(newState)

        // broadcast action
        loopbacks.forEach {
            it.send(action)
        }
    }
}
