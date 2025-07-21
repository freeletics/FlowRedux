package com.freeletics.flowredux2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * A live state machine that was created by a [FlowReduxStateMachineFactory]. [state] is
 * a container allowing either access the current state or collecting the current state.
 * [dispatchAction] and [dispatch] can be used to send actions to the state machine.
 */
public class FlowReduxStateMachine<S : Any, A : Any> internal constructor(
    private val _state: S,
    private val inputActions: Channel<A>,
    private val scope: CoroutineScope,
) {
    public val state: S
        get() = _state

    public val dispatchAction: (A) -> Unit = {
        scope.launch {
            dispatch(it)
        }
        Unit
    }

    public suspend fun dispatch(action: A) {
        inputActions.send(action)
    }
}
