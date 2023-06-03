package com.freeletics.flowredux

import com.freeletics.flowredux.sideeffects.Action
import com.freeletics.flowredux.sideeffects.ChangeStateAction
import com.freeletics.flowredux.sideeffects.ExternalWrappedAction
import com.freeletics.flowredux.sideeffects.InitialStateAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

internal fun <A : Any, S : Any> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffects: Iterable<SideEffect<S, A>>,
): Flow<S> = flow {
    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }

    // Emit the initial state
    emit(currentState)

    val loopbacks = sideEffects.map {
        Channel<Action<S, A>>()
    }
    val sideEffectActions = sideEffects.mapIndexed { index, sideEffect ->
        val actionsFlow = loopbacks[index].consumeAsFlow()
        sideEffect(actionsFlow, getState)
    }
    val upstreamActions = this@reduxStore
        .map<A, Action<S, A>> { ExternalWrappedAction(it) }
        .onStart {
            emit(InitialStateAction())
        }

    (sideEffectActions + upstreamActions).merge().collect { action ->
        // Change state
        if (action is ChangeStateAction<S, A>) {
            val newState = action.reduce(currentState)
            if (currentState !== newState) {
                currentState = newState
                emit(newState)

                // broadcast state change
                loopbacks.forEach {
                    it.send(InitialStateAction())
                }
            }
        } else {
            // broadcast action
            loopbacks.forEach {
                it.send(action)
            }
        }
    }
}
