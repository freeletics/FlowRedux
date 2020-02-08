package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.dsl.flow.flatMapWithPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Observe for the lifetime of the whole redux store.
 * This observation of a flow is independent from any state the store is in nor from actions
 * that have been triggered.
 *
 * A typical use case would be something like observing a database.
 */
internal class StoreWideObserveBuilderBlock<T, S, A>(
    private val flow: Flow<T>,
    private val flatMapPolicy: FlatMapPolicy,
    private val block: StoreWideObserverBlock<T, S>
) : StoreWideBuilderBlock<S, A>() {

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {

        val sideEffect: SideEffect<S, Action<S, A>> = { actions: Flow<Action<S, A>>,
            state: StateAccessor<S> ->

            flow.flatMapWithPolicy(flatMapPolicy) {
                setStateFlow(value = it, stateAccessor = state)
            }
        }

        return listOf(sideEffect)
    }

    private suspend fun setStateFlow(
        value: T,
        stateAccessor: StateAccessor<S>
    ): Flow<Action<S, A>> = flow {

        val setState = SetStateImpl<S>(
            defaultRunIf = { true },
            invokeCallback = { runIf, reduce ->
                emit(
                    SelfReducableAction<S, A>(
                        loggingInfo = "observe<Flow>",
                        reduce = reduce,
                        runReduceOnlyIf = runIf
                    )
                )
            }
        )
        block(value, stateAccessor, setState)
    }
}

typealias StoreWideObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: SetState<S>) -> Unit
