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
import kotlin.reflect.KClass

// TODO @DslMarker

class InStateBuilderBlock<S : Any, SubState : S, A : Any>(
    val _subStateClass: KClass<SubState>
) : StoreWideBuilderBlock<S, A>() {

    val _inStateSideEffectBuilders = ArrayList<InStateSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionSideEffectBuilder<S, A, SubState>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            subStateClass = _subStateClass,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    fun <T> observeWhileInState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: InStateObserverBlock<T, S>
    ) {
        _inStateSideEffectBuilders.add(
            ObserveInStateBuilder(
                subStateClass = _subStateClass,
                flow = flow,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     *
     * This does not cancel any ongoing block when the state changes.
     *
     * TODO add a sample
     */
    fun onEnter(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        block: InStateOnEnterBlock<S>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateBuilder(
                subStateClass = _subStateClass,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 */
abstract class InStateSideEffectBuilder<S, A> internal constructor() {
    internal abstract fun generateSideEffect(): SideEffect<S, Action<S, A>>
}

class OnActionSideEffectBuilder<S : Any, A : Any, SubState : S>(
    private val subStateClass: KClass<SubState>,
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
) : InStateSideEffectBuilder<S, A>() {

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

                println("filter $action $conditionHolds")

                conditionHolds

            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> it.action as A // subActionClass.cast(it.action) // TODO kotlin native supported?
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
                            reduce = it
                        )
                    )
                }
            )
        }

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
}


typealias OnActionBlock<S, A> = suspend (action: A, getState: StateAccessor<S>, setState: SetState<S>) -> Unit

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
internal class ObserveInStateBuilder<T, S : Any, A : Any>(
    private val subStateClass: KClass<out S>,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, S>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, state: StateAccessor<S> ->
            when (flatMapPolicy) {
                FlatMapPolicy.LATEST ->
                    actions
                        .mapStateChanges(stateAccessor = state, stateToObserve = subStateClass)
                        .flatMapLatest { stateChange ->
                            when (stateChange) {
                                MapStateChange.StateChanged.ENTERED ->
                                    flow.flatMapLatest {
                                        setStateFlow(value = it, stateAccessor = state)
                                    }
                                MapStateChange.StateChanged.LEFT -> flow { }
                            }
                        }
                FlatMapPolicy.CONCAT -> actions
                    .mapStateChanges(stateAccessor = state, stateToObserve = subStateClass)
                    .flatMapLatest { stateChanged ->
                        when (stateChanged) {
                            MapStateChange.StateChanged.ENTERED ->
                                flow.flatMapConcat {
                                    setStateFlow(value = it, stateAccessor = state)
                                }
                            MapStateChange.StateChanged.LEFT -> flow { }
                        }
                    }
                FlatMapPolicy.MERGE -> actions
                    .mapStateChanges(stateAccessor = state, stateToObserve = subStateClass)
                    .flatMapLatest { stateChange ->
                        when (stateChange) {
                            MapStateChange.StateChanged.ENTERED ->
                                flow.flatMapMerge {
                                    setStateFlow(value = it, stateAccessor = state)
                                }
                            MapStateChange.StateChanged.LEFT -> flow { }
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
            emit(
                SelfReducableAction<S, A>(
                    loggingInfo = "observeWhileInState<${subStateClass.simpleName}>",
                    reduce = it
                )
            )
        }
    }
}

typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit
