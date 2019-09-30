package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import kotlin.reflect.full.isSubclassOf

class InStateBuilderBlock<S : Any, SubState : S, A : Any>(
    internal val subStateClass: KClass<SubState>
) : StoreWideBuilderBlock<S, A>() {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _onActionSideEffectBuilders = ArrayList<OnActionSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionSideEffectBuilder<S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _onActionSideEffectBuilders.add(builder)
    }

    // TODO function to observe as long as we are in that state (i.e. observe a database as long
    //  as we are in that state.

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {

        return _onActionSideEffectBuilders.map { onAction ->
            object : SideEffect<S, Action<S, A>> {
                override fun invoke(
                    actions: Flow<Action<S, A>>,
                    state: StateAccessor<S>
                ): Flow<Action<S, A>> {
                    return when (onAction.flatMapPolicy) {
                        FlatMapPolicy.LATEST ->
                            actionsFilterFactory(
                                actions,
                                state,
                                subStateClass,
                                onAction
                            )
                                .flatMapLatest { action ->
                                    onActionSideEffectFactory(
                                        action = action,
                                        stateAccessor = state,
                                        onAction = onAction
                                    )
                                }

                        FlatMapPolicy.MERGE ->
                            actionsFilterFactory(
                                actions,
                                state,
                                subStateClass,
                                onAction
                            )
                                .flatMapMerge { action ->
                                    onActionSideEffectFactory(
                                        action = action,
                                        stateAccessor = state,
                                        onAction = onAction
                                    )
                                }

                        FlatMapPolicy.CONCAT ->
                            actionsFilterFactory(
                                actions,
                                state,
                                subStateClass,
                                onAction
                            )
                                .flatMapConcat { action ->
                                    onActionSideEffectFactory(
                                        action = action,
                                        stateAccessor = state,
                                        onAction = onAction
                                    )
                                }
                    }
                }
            }
        }
    }

    /**
     * Creates a Flow that filters for a given (sub)state and (sub)action and returns a
     * Flow of (sub)action
     */
    private fun actionsFilterFactory(
        actions: Flow<Action<S, out A>>,
        state: StateAccessor<S>,
        subStateClass: KClass<out S>,
        onAction: OnActionSideEffectBuilder<S, out A>
    ): Flow<A> =
        actions
            .filter { action ->
                // TODO use .isInstance() instead of isSubclassOf() as it should work in kotlin native
                val conditionHolds = subStateClass.isSubclassOf(state()::class) &&
                    action is ExternalWrappedAction &&
                    onAction.subActionClass.isSubclassOf(action.action::class)

                println("filter $action $conditionHolds")

                conditionHolds

            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> onAction.subActionClass.cast(it.action) // TODO kotlin native supported?
                    is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                }
            }

    private fun onActionSideEffectFactory(
        action: A,
        stateAccessor: StateAccessor<S>,
        onAction: OnActionSideEffectBuilder<S, A>
    ): Flow<Action<S, A>> =
        flow {
            onAction.onActionBlock.invoke(
                stateAccessor,
                {
                    println("would like to set state because $action to ${it(stateAccessor())}")
                    emit(SelfReducableAction<S, A>(it))
                },
                action
            )
        }
}

class OnActionSideEffectBuilder<S : Any, A : Any>(
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
) {

}

typealias OnActionBlock<S, A> = suspend (getState: StateAccessor<S>, setState: SetState<S>, action: A) -> Unit
