@file:Suppress("UNCHECKED_CAST")

package com.freeletics.flowredux.dsl.internal

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.ExecutionPolicy
import com.freeletics.flowredux.dsl.FlowReduxDsl
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.dsl.flow.flatMapWithExecutionPolicy
import com.freeletics.flowredux.dsl.flow.whileInState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.mapNotNull

@FlowPreview
@ExperimentalCoroutinesApi
internal class OnActionInStateSideEffectBuilder<InputState : S, S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    internal val subActionClass: KClass<out A>,
    internal val executionPolicy: ExecutionPolicy,
    internal val handler: suspend (action: A, state: State<InputState>) -> ChangedState<S>
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->

            actions.whileInState(isInState, getState) { inStateAction ->
                inStateAction.mapNotNull {
                    when (it) {
                        is ExternalWrappedAction<*, *> -> if (subActionClass.isInstance(it.action)) {
                            it.action as A
                        } else {
                            null
                        }
                        is ChangeStateAction -> null
                        is InitialStateAction -> null
                    }
                }
                    .flatMapWithExecutionPolicy(executionPolicy) { action ->
                        onActionSideEffectFactory(
                            action = action,
                            getState = getState
                        )
                    }
            }
        }
    }

    private fun onActionSideEffectFactory(
        action: A,
        getState: GetState<S>
    ): Flow<Action<S, A>> =
        flow {

            runOnlyIfInInputState(getState, isInState) { inputState ->
                val changeState = handler(
                    action,
                    State(inputState)
                )

                emit(
                    ChangeStateAction<S, A>(
                        changedState = changeState,
                        runReduceOnlyIf = { state -> isInState(state) }
                    )
                )

            }
        }
}
