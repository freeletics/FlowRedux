package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

/**
 * A builder to create a [SideEffect] that observes a Flow<T> as long as the redux store is in
 * the given state. We use is instance of check to check if a new state has been reached and Flow<T>
 * is closed.
 */
internal class ObserveInStateSideEffectBuilder<T, S : Any, A : Any>(
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
    ): Flow<Action<S, A>> =
        flow {
            block(value, stateAccessor) {
                emit(
                    SelfReducableAction<S, A>(
                        loggingInfo = "observeWhileInState<${subStateClass.simpleName}>",
                        reduce = it
                    )
                )
            }
        }
}typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit