package com.freeletics.flowredux.util

import com.freeletics.flowredux.sideeffects.Action
import com.freeletics.flowredux.sideeffects.ChangeStateAction
import com.freeletics.flowredux.sideeffects.GetState
import com.freeletics.flowredux.sideeffects.SideEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

internal fun <S, A> Flow<Action<S, A>>.whileInState(
    isInState: SideEffect.IsInState<S>,
    getState: GetState<S>,
    transform: suspend (Flow<Action<S, A>>) -> Flow<ChangeStateAction<S, A>>,
) = channelFlow {
    var currentChannel: Channel<Action<S, A>>? = null

    // collect upstream
    collect { value ->
        if (isInState.check(getState())) {
            // if there is no Channel yet the state machine just entered the state
            if (currentChannel == null) {
                currentChannel = Channel()
                launch {
                    // transform actions sent to the the channel with the given function
                    // and emit the result downstream
                    transform(currentChannel!!.consumeAsFlow())
                        .collect { send(it) }
                }
            }
            // send the action to the transform because the state machine is in state
            currentChannel!!.send(value)
        } else {
            // closing the channel with an exception will cancel the FlowCollector that
            // collects it and therefore cancels the collection
            currentChannel?.close(CancellationException("StateMachine left the state"))
            currentChannel = null
        }
    }
}
