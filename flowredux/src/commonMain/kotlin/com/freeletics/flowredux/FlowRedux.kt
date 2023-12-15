package com.freeletics.flowredux

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.reduce
import com.freeletics.flowredux.sideeffects.GetState
import com.freeletics.flowredux.sideeffects.ManagedSideEffect
import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun <A : Any, S : Any> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffectBuilders: Iterable<SideEffectBuilder<out S, S, A>>,
): Flow<S> = channelFlow {
    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }

    val stateChanges = Channel<ChangedState<S>>(Channel.UNLIMITED)
    val sideEffects = sideEffectBuilders.map { ManagedSideEffect(it, this@channelFlow, getState, stateChanges) }

    // Emit the initial state
    send(currentState)
    sideEffects.forEach {
        it.startIfNeeded(currentState)
    }

    val mutex = Mutex()

    launch {
        stateChanges.consumeAsFlow().collect { action ->
            val newState = action.reduce(currentState)
            if (currentState !== newState) {
                currentState = newState

                mutex.withLock {
                    sideEffects.forEach {
                        it.cancelIfNeeded(newState)
                    }

                    send(newState)

                    sideEffects.forEach {
                        it.startIfNeeded(currentState)
                    }
                }
            }
        }
    }

    this@reduxStore.collect { action ->
        mutex.withLock {
            sideEffects.forEach {
                it.sendAction(action, currentState)
            }
        }
    }
}
