package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
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
// TODO rename Observe to Collect to match with flow api naming conventions
internal class ObserveInStateSideEffectBuilder<T, S : Any, A : Any>(
    private val subStateClass: KClass<out S>,
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: InStateObserverBlock<T, S>
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        return { actions: Flow<Action<S, A>>, state: StateAccessor<S> ->
            actions
                .mapStateChanges(stateAccessor = state, stateToObserve = subStateClass)
                .flatMapWithPolicy(flatMapPolicy) { stateChange ->
                    when (stateChange) {
                        MapStateChange.StateChanged.ENTERED ->
                            flow.flatMapLatest {
                                // TODO is it actually always flatMapLatest or also flatMapWithPolicy
                                setStateFlow(value = it, stateAccessor = state)
                            }
                        MapStateChange.StateChanged.LEFT -> flow { }
                    }
                }
        }
    }

    private suspend fun setStateFlow(
        value: T,
        stateAccessor: StateAccessor<S>
    ): Flow<Action<S, A>> =
        flow {
            val setState = SetStateImpl<S>(
                defaultRunIf = { state -> subStateClass.isInstance(state) },
                invokeCallback = { runIf, reduce ->
                    emit(
                        SelfReducableAction<S, A>(
                            loggingInfo = "observeWhileInState<${subStateClass.simpleName}>",
                            reduce = reduce,
                            runReduceOnlyIf = runIf
                        )
                    )
                }
            )
            block(value, stateAccessor, setState)
        }
}

typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit