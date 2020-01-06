package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private fun actionsFilterFactory(
        actions: Flow<Action<S, out A>>,
        state: StateAccessor<S>,
        subStateClass: KClass<out S>
    ): Flow<A> =
        actions
            .filter { action ->
                // TODO use .isInstance() instead of isSubclassOf() as it should work in kotlin native
                val conditionHolds = subStateClass.isInstance(state()) &&
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

            when (flatMapPolicy) {
                FlatMapPolicy.LATEST ->
                    actionsFilterFactory(
                        actions,
                        state,
                        subStateClass
                    )
                        .flatMapLatest { action ->
                            onActionSideEffectFactory(
                                action = action,
                                stateAccessor = state
                            )
                        }

                FlatMapPolicy.MERGE ->
                    actionsFilterFactory(
                        actions,
                        state,
                        subStateClass
                    )
                        .flatMapMerge { action ->
                            onActionSideEffectFactory(
                                action = action,
                                stateAccessor = state
                            )
                        }

                FlatMapPolicy.CONCAT ->
                    actionsFilterFactory(
                        actions,
                        state,
                        subStateClass
                    )
                        .flatMapConcat { action ->
                            onActionSideEffectFactory(
                                action = action,
                                stateAccessor = state
                            )
                        }
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
                        .mapStateChanges(state)
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
                    .mapStateChanges(state)
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
                    .mapStateChanges(state)
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

    /**
     * Internal implementation of an operator that keeps track if you a certain state has been
     * entered or left.
     */
    private class MapStateChange<S : Any, A : Any>(
        actions: Flow<Action<S, A>>,
        state: StateAccessor<S>,
        subStateClass: KClass<out S>
    ) {

        private enum class InternalStateChangedSubscription {
            ENTERED, LEFT, NOT_CHANGED
        }

        /**
         * Information about whether a state machine entered or did left a certain state
         */
        internal enum class StateChanged {
            /**
             * Entered the specified state
             */
            ENTERED,
            /**
             * Left the specified state
             */
            LEFT
        }

        private val mutex = Mutex()
        private var lastState: S? = null

        internal val flow: Flow<StateChanged> = actions.map {
            mutex.withLock {

                val state = state()
                val previousState = lastState
                val isInExpectedState = subStateClass.isInstance(state)
                val previousStateInExpectedState = if (previousState == null) {
                    false
                } else {
                    subStateClass.isInstance(previousState)
                }

                if (previousState == null) {
                    if (isInExpectedState) {
                        InternalStateChangedSubscription.ENTERED
                    } else {
                        InternalStateChangedSubscription.NOT_CHANGED
                    }
                } else {
                    when {
                        isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.NOT_CHANGED
                        isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.ENTERED
                        !isInExpectedState && previousStateInExpectedState -> InternalStateChangedSubscription.LEFT
                        !isInExpectedState && !previousStateInExpectedState -> InternalStateChangedSubscription.NOT_CHANGED
                        else -> throw IllegalStateException(
                            "An internal error occurred: " +
                                "isInExpectedState: $isInExpectedState" +
                                "and previousStateInExpectedState: $previousStateInExpectedState " +
                                "is not possible. Please file an issue on Github."
                        )

                    }
                }.also {
                    lastState = state
                }

            }
        }
            .filter { it != InternalStateChangedSubscription.NOT_CHANGED }
            .distinctUntilChanged()
            .map {
                when (it) {
                    InternalStateChangedSubscription.ENTERED -> StateChanged.ENTERED
                    InternalStateChangedSubscription.LEFT -> StateChanged.LEFT
                    InternalStateChangedSubscription.NOT_CHANGED -> throw IllegalStateException(
                        "Internal Error occurred. File an issue on Github."
                    )
                }
            }
    }

    private fun Flow<Action<S, A>>.mapStateChanges(
        state: StateAccessor<S>
    ): Flow<MapStateChange.StateChanged> = MapStateChange<S, A>(
        actions = this,
        subStateClass = subStateClass,
        state = state
    ).flow
}

typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit
