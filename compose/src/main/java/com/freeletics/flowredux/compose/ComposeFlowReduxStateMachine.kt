package com.freeletics.flowredux.compose

import androidx.compose.runtime.*
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Get a Compose [State] object from a [FlowReduxStateMachine].
 */
@Composable
fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.rememberState(): State<S> {
    return produceState(initialValue = this.initialState, this) {
        state.drop(1) // skip the first one as it is the initial state which is already submitted with produceState's initial state
            .collect { value = it }
    }
}

/**
 * This class is the return type of [FlowReduxStateMachine.stateAndDispatch()].
 * It is mainly meant to be used with Kotlin's deconstructions feature as follows:
 *
 * ```kotlin
 * val myStateMachine = MyFlowReduxStateMachine()
 *
 * @Composable
 * fun MyUi(){
 *   val (state, dispatch) = myStateMachine.rememberStateAndDispatch()
 *   ...
 * }
 * ```
 */
data class StateAndDispatch<S : Any, A : Any>(
    val state: State<S>,
    val dispatchAction: (A) -> Unit
)

/**
 * Convenient way to get a Compose [State] to get state update of a [FlowReduxStateMachine]
 * and a function of type `(Action) -> Unit` to dispatch Actions to a [FlowReduxStateMachine].
 * Under the hood `State` will be updated only as long as the surrounding Composable is in use.
 * The dispatch function `(Action) -> Unit` is tight to the same Composable component and launches
 * a coroutine to dispatch actions async. to the `FlowReduxStateMachine`
 */
@Composable
fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.rememberStateAndDispatch(): StateAndDispatch<S, A> {
    val stateMachine = this
    val scope = rememberCoroutineScope()
    return StateAndDispatch(
        state = stateMachine.rememberState(),
        dispatchAction = remember(stateMachine) {
            { action: A ->
                scope.launch {
                    stateMachine.dispatch(action)
                }
            }
        })
}
