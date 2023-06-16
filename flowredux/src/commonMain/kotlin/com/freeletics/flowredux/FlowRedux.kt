package com.freeletics.flowredux

import com.freeletics.flowredux.dsl.reduce
import com.freeletics.flowredux.sideeffects.Action
import com.freeletics.flowredux.sideeffects.ExternalWrappedAction
import com.freeletics.flowredux.sideeffects.GetState
import com.freeletics.flowredux.sideeffects.InitialStateAction
import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import com.freeletics.flowredux.sideeffects.produceStateGuarded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

internal fun <A : Any, S : Any> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    sideEffectBuilders: Iterable<SideEffectBuilder<out S, S, A>>,
): Flow<S> = channelFlow {
    var currentState: S = initialStateSupplier()
    val getState: GetState<S> = { currentState }

    val sideEffects = sideEffectBuilders.map { it.build() }

    // Emit the initial state
    send(currentState)

    val loopbacks = sideEffects.map {
        Channel<Action<A>>()
    }

    launch {
        val sideEffectActions = sideEffects.mapIndexed { index, sideEffect ->
            val actionsFlow = loopbacks[index].consumeAsFlow()
            sideEffect.produceStateGuarded(actionsFlow, getState)
        }

        sideEffectActions.merge().collect { action ->
            val newState = action.reduce(currentState)
            if (currentState !== newState) {
                currentState = newState
                send(newState)

                // broadcast state change
                loopbacks.forEach {
                    it.send(InitialStateAction())
                }
            }
        }
    }

    this@reduxStore
        .map<A, Action<A>> { ExternalWrappedAction(it) }
        .onStart {
            emit(InitialStateAction())
        }
        .collect { action ->
            // broadcast action
            loopbacks.forEach {
                it.send(action)
            }
        }
}
