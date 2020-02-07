package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

class OnActionInStateSideEffectBuilder<S : Any, A : Any, SubState : S>(
    private val subStateClass: KClass<SubState>,
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, state: StateAccessor<S> ->

            actions
                .filterStateAndUnwrapExternalAction(
                    stateAccessor = state,
                    subStateClass = subStateClass
                )
                .flatMapWithPolicy(flatMapPolicy) { action ->
                    onActionSideEffectFactory(
                        action = action,
                        stateAccessor = state
                    )
                }
        }
    }

    /**
     * Creates a Flow that filters for a given (sub)state and (sub)action and returns a
     * Flow of (sub)action
     */
    private fun Flow<Action<S, out A>>.filterStateAndUnwrapExternalAction(
        stateAccessor: StateAccessor<S>,
        subStateClass: KClass<out S>
    ): Flow<A> =
        this
            .filter { action ->
                val conditionHolds = subStateClass.isInstance(stateAccessor()) &&
                    action is ExternalWrappedAction &&
                    subActionClass.isInstance(action.action)
                conditionHolds
            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> it.action as A
                    is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                    is InitialStateAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                }
            }

    private fun onActionSideEffectFactory(
        action: A,
        stateAccessor: StateAccessor<S>
    ): Flow<Action<S, A>> =
        flow {
            onActionBlock.invoke(
                action,
                stateAccessor,
                {
                    emit(
                        SelfReducableAction<S, A>(
                            loggingInfo = "Caused by on<$action>",
                            reduce = it,
                            runReduceOnlyIf = { state -> subStateClass.isInstance(state) }
                        )
                    )
                }
            )
        }
}

typealias OnActionBlock<S, A> = suspend (action: A, getState: StateAccessor<S>, setState: SetState<S>) -> Unit
