package com.freeletics.flowredux2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.sideeffects.reduxStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@ExperimentalCoroutinesApi
@Composable
public fun <S : Any, A : Any> stateMachine(
    initialState: S,
    specBlock: @DisallowComposableCalls FlowReduxBuilder<S, A>.() -> Unit,
): FlowReduxStateMachine<State<S>, A> {
    val sideEffectBuilders = remember(specBlock) {
        FlowReduxBuilder<S, A>(null).apply(specBlock).sideEffectBuilders
    }

    // use LossyStateHolder because when using this from compose the caller can use
    // standard compose mechanisms for keeping/saving the state
    return stateMachine(LossyStateHolder({ initialState }), sideEffectBuilders, null)
}

/**
 * Create and start running a [FlowReduxStateMachine] that exposes a compose [State]. The state machine
 * will stay active as long as this method stays in the current composition.
 *
 * Note: [initializeWith] and [FlowReduxStateMachineFactory.spec] need to be called before this method.
 */
@Composable
public fun <S : Any, A : Any> FlowReduxStateMachineFactory<S, A>.produceStateMachine(): FlowReduxStateMachine<State<S>, A> {
    checkInitialized()
    return stateMachine(stateHolder, sideEffectBuilders, logger)
}

@Composable
internal fun <S : Any, A : Any> stateMachine(
    stateHolder: StateHolder<S>,
    sideEffectBuilders: List<SideEffectBuilder<*, S, A>>,
    logger: TaggedLogger?,
): FlowReduxStateMachine<State<S>, A> {
    val inputActions = remember(sideEffectBuilders) { Channel<A>() }
    val state = produceState(stateHolder.getState(), sideEffectBuilders, inputActions) {
        inputActions
            .receiveAsFlow()
            .reduxStore(value, sideEffectBuilders, logger)
            .onEach { stateHolder.saveState(it) }
            .collect { value = it }
    }

    val scope = rememberCoroutineScope()
    return remember(state, inputActions, scope) {
        FlowReduxStateMachine(state, inputActions, scope)
    }
}
