package com.freeletics.flowredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.select

/**
 * Creates a Redux store with an initial state. If your initial state is an expensive computation
 * consider using [reduxStore]  which has an initialStateSupplier as parameter that produces the
 * first state lazily once the flow starts.

@ExperimentalCoroutinesApi
@FlowPreview
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
@FlowPreview
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

    suspend fun callReducer(origin: String, action: A) {
        logger?.log("$origin: action $action received")

        // Change state
        val newState: S = reducer(currentState, action)
        logger?.log("$origin: reducing $currentState with $action -> $newState")
        currentState = newState
        emit(newState)

        // broadcast action
        loopback.emit(action)
    }

    coroutineScope {
        val upstreamChannel = produceIn(this)
        val sideEffectChannels = sideEffects.map { it(loopback, getState).produceIn(this) }

        while (!upstreamChannel.isClosedForReceive || sideEffectChannels.any { !it.isClosedForReceive }) {
            select<Unit> {
                sideEffectChannels.forEachIndexed { index, sideEffectChannel ->
                    if (!sideEffectChannel.isClosedForReceive) {
                        sideEffectChannel.onReceiveCatching { result ->
                            val action = result.getOrNull()
                            if (action != null) {
                                callReducer("SideEffect$index", action)
                            }
                        }
                    }
                }

                if (!upstreamChannel.isClosedForReceive) {
                    upstreamChannel.onReceiveCatching { result ->
                        val action = result.getOrNull()
                        if (action != null) {
                            callReducer("Upstream", action)
                        }
                    }
                }
            }
        }
    }
}
