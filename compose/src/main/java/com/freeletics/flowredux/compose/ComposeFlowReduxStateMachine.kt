package com.freeletics.flowredux.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.asState(): State<S> {
    return produceState(initialValue = this.initialStateSupplier()) {
        state.drop(1) // skip the first one as it is the initial state which is already submitted with produceState's initial state
            .collect { value = it }
    }
}

data class StateAndDispatch<S : Any, A : Any>(
    val state: State<S>,
    val dispatchAction: (A) -> Unit
)

@Composable
fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.stateAndDispatch(): StateAndDispatch<S, A> {
    val stateMachine = this
    val scope = rememberCoroutineScope()
    return StateAndDispatch(state = stateMachine.asState(), dispatchAction = { action: A ->
        scope.launch { stateMachine.dispatch(action) }
    })
}
