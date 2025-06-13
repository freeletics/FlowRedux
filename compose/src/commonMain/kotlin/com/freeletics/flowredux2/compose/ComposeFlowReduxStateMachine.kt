package com.freeletics.flowredux2.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.freeletics.flowredux2.FlowReduxStateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/**
 * Get a Compose [State] object from a [FlowReduxStateMachine].
 *
 * The returned [State.value] can be `null`.
 * `null` means that the [FlowReduxStateMachine] has not emitted a state yet.
 * That is the case when the coroutine has launched to collect [FlowReduxStateMachine.state]
 * but since it runs async in a coroutine (a new coroutines is launched under
 * the hood of [rememberState]) the [FlowReduxStateMachine] has not emitted state yet
 * while Jetpack Compose continue its work on the main thread.
 * Therefore, `null` is used as some sort of initial value and the first value of
 * [FlowReduxStateMachine] is emitted just a bit later (non null value then).
 */
@ExperimentalCoroutinesApi
@Composable
public fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.rememberState(): State<S?> {
    return produceState<S?>(initialValue = null, this) {
        state.collect { value = it }
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
public data class StateAndDispatch<S : Any, A : Any>(
    public val state: State<S?>,
    public val dispatchAction: (A) -> Unit,
)

/**
 * Convenient way to get a Compose [State] to get state update of a [FlowReduxStateMachine]
 * and a function of type `(Action) -> Unit` to dispatch Actions to a [FlowReduxStateMachine].
 * Under the hood `State` will be updated only as long as the surrounding Composable is in use.
 * The dispatch function `(Action) -> Unit` is tight to the same Composable component and launches
 * a coroutine to dispatch actions async. to the `FlowReduxStateMachine`.
 *
 * The returned [State.value] can be `null`.
 * `null` means that the [FlowReduxStateMachine]  has not emitted a state yet.
 * That is the case when the coroutine has launched to collect [FlowReduxStateMachine.state]
 * but since it runs async in a coroutine (a new coroutine is launched under
 * the hood of [rememberStateAndDispatch]) the [FlowReduxStateMachine] has not emitted state yet
 * while Jetpack Compose continue its work on the main thread.
 * Therefore, `null` is used as some sort of initial value and the first value of
 * [FlowReduxStateMachine] is emitted just a bit later (non null value then).
 */
@ExperimentalCoroutinesApi
@Composable
public fun <S : Any, A : Any> FlowReduxStateMachine<S, A>.rememberStateAndDispatch(): StateAndDispatch<S, A> {
    val stateMachine = this
    val scope = rememberCoroutineScope()

    val state = stateMachine.rememberState()
    val dispatchAction = remember(scope, stateMachine) {
        { action: A ->
            scope.launch {
                stateMachine.dispatch(action)
            }
            Unit
        }
    }

    return remember(state, dispatchAction) {
        StateAndDispatch(
            state = state,
            dispatchAction = dispatchAction,
        )
    }
}
