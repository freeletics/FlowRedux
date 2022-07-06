package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.FlowReduxDsl
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.dsl.flow.flatMapWithExecutionPolicy
import com.freeletics.flowredux.dsl.flow.mapToIsInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
@ExperimentalCoroutinesApi
@FlowPreview
internal class CollectInStateBuilder<T, InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    private val flow: Flow<T>,
    private val executionPolicy: ExecutionPolicy,
    private val handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
                .mapToIsInState(isInState, getState)
                .flatMapLatest { inState ->
                    if (inState) {
                        flow.flatMapWithExecutionPolicy(executionPolicy) {
                            setStateFlow(value = it, getState = getState)
                        }
                    } else {
                        emptyFlow()
                    }
                }
        }

    }

    private suspend fun setStateFlow(
        value: T,
        getState: GetState<S>
    ): Flow<Action<S, A>> = flow {

        runOnlyIfInInputState(getState, isInState) { inputState ->
            val changeState = handler(value, State(inputState))
            emit(
                ChangeStateAction<S, A>(
                    changedState = changeState,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
    }
}
