package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.Reducer
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

class OnActionInStateSideEffectBuilder<S : Any, A : Any>(
    private val isInState: (S) -> Boolean,
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->

            actions
                .filterStateAndUnwrapExternalAction(
                    getState = getState
                )
                .flatMapWithPolicy(flatMapPolicy) { action ->
                    onActionSideEffectFactory(
                        action = action,
                        getState = getState
                    )
                }
        }
    }

    /**
     * Creates a Flow that filters for a given (sub)state and (sub)action and returns a
     * Flow of (sub)action
     */
    private fun Flow<Action<S, out A>>.filterStateAndUnwrapExternalAction(
        getState: GetState<S>
    ): Flow<A> =
        this
            .filter { action ->
                val conditionHolds = isInState(getState()) &&
                        action is ExternalWrappedAction &&
                        subActionClass.isInstance(action.action)
                conditionHolds
            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> it.action as A
                    is SetStateAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                    is InitialStateAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                }
            }

    private fun onActionSideEffectFactory(
        action: A,
        getState: GetState<S>
    ): Flow<Action<S, A>> =
        flow {
            val reduce = onActionBlock.invoke(
                action,
                getState
            )

            emit(
                SetStateAction<S, A>(
                    loggingInfo = "Caused by on<$action>",
                    reduce = reduce,
                    runReduceOnlyIf = { state -> isInState(state) }
                )
            )
        }
}

typealias OnActionBlock<S, A> = suspend (action: A, getState: GetState<S>) -> ReduceFunc<S>
