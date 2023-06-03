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

internal fun <A : Any, S : Any> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffectBuilders: Iterable<SideEffectBuilder<out S, S, A>>,
): Flow<S> = channelFlow {
    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }

    val stateChanges = Channel<ChangedState<S>>()
    val sideEffects = sideEffectBuilders.map { ManagedSideEffect(it, this@channelFlow, getState, stateChanges) }

    // Emit the initial state
    send(currentState)
    sideEffects.forEach {
        it.sendStateChange(currentState)
    }

    launch {
        stateChanges.consumeAsFlow().collect { action ->
            val newState = action.reduce(currentState)
            if (currentState !== newState) {
                currentState = newState

                sideEffects.forEach {
                    it.cancelIfNeeded(newState)
                }

                send(newState)

                sideEffects.forEach {
                    it.sendStateChange(currentState)
                }
            }
        }
    }

    this@reduxStore.collect { action ->
        sideEffects.forEach {
            it.sendAction(action, currentState)
        }
    }
}
