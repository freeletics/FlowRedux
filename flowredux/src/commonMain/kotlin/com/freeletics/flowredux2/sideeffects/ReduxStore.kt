package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.NoStateChangeSkipEmission
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.logI
import com.freeletics.flowredux2.reduce
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun <A : Any, S : Any> Flow<A>.reduxStore(
    initialState: S,
    sideEffectBuilders: Iterable<SideEffectBuilder<out S, S, A>>,
    logger: TaggedLogger?,
): Flow<S> = channelFlow {
    var currentState: S = initialState
    val getState: GetState<S> = { currentState }

    val stateChanges = Channel<ChangedState<S>>(Channel.UNLIMITED)
    val sideEffects = sideEffectBuilders.map { ManagedSideEffect(it, this@channelFlow, getState, stateChanges) }

    // Emit the initial state
    send(currentState)
    logger.logI { "Started with state $currentState" }
    sideEffects.forEach {
        it.startIfNeeded(currentState)
    }

    val mutex = Mutex()

    launch {
        stateChanges.consumeAsFlow().filter { it != NoStateChangeSkipEmission }.collect { action ->
            val newState = action.reduce(currentState)
            if (currentState !== newState) {
                currentState = newState

                mutex.withLock {
                    sideEffects.forEach {
                        it.cancelIfNeeded(newState)
                    }

                    logger.logI { "New state $action" }
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
            logger.logI { "Received $action" }
            sideEffects.forEach {
                it.sendAction(action, currentState)
            }
        }
    }
}
