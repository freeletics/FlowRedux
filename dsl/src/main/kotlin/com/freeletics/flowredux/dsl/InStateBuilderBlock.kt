package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

    // TODO merge this with _onActionSideEffectBuilders
    private val _observeInStateBuilders = ArrayList<InStateSideEffectBuilder<S, A>>()

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

    fun <T> observe(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: StoreWideObserverBlock<T, S>
    ) {
        _observeInStateBuilders.add(
            ObserveInStateBuilder(
                subStateClass = subStateClass,
                flow = flow,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
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
        } + _observeInStateBuilders.map { it.generateSideEffect() }
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

                // println("filter $action $conditionHolds")

                conditionHolds

            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> onAction.subActionClass.cast(it.action) // TODO kotlin native supported?
                    is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                    is InitialStateAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
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

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 */
abstract class InStateSideEffectBuilder<S, A> internal constructor() {
    internal abstract fun generateSideEffect(): SideEffect<S, Action<S, A>>
}

class OnActionSideEffectBuilder<S : Any, A : Any>(
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        TODO("move side effect generation in here") //To change body of created functions use File | Settings | File Templates.
    }
}


typealias OnActionBlock<S, A> = suspend (getState: StateAccessor<S>, setState: SetState<S>, action: A) -> Unit

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
internal class ObserveInStateBuilder<T, S : Any, A : Any>(
    private val subStateClass: KClass<out S>,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: StoreWideObserverBlock<T, S>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return object : SideEffect<S, Action<S, A>> {
            override fun invoke(
                actions: Flow<Action<S, A>>,
                state: StateAccessor<S>
            ): Flow<Action<S, A>> =
                when (flatMapPolicy) {
                    FlatMapPolicy.LATEST ->
                        actions
                            .filterState(state)
                            .flatMapLatest {
                                flow.flatMapLatest {
                                    setStateFlow(value = it, stateAccessor = state)
                                }
                            }
                    FlatMapPolicy.CONCAT -> actions
                        .filterState(state)
                        .flatMapLatest {
                            flow.flatMapConcat {
                                setStateFlow(value = it, stateAccessor = state)
                            }
                        }
                    FlatMapPolicy.MERGE -> actions
                        .filterState(state)
                        .flatMapLatest {
                            flow.flatMapMerge {
                                setStateFlow(value = it, stateAccessor = state)
                            }
                        }
                }
        }
    }

    private suspend fun setStateFlow(
        value: T,
        stateAccessor: StateAccessor<S>
    ): Flow<Action<S, A>> = flow {
        block(value, stateAccessor) {
            emit(SelfReducableAction<S, A>(it))
        }
    }

    private fun Flow<Action<S, A>>.filterState(
        state: StateAccessor<S>
    ): Flow<Action<S, A>> =
        this.filter { subStateClass.isInstance(state()) }
            .distinctUntilChangedBy { state()::class }
}

typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit
