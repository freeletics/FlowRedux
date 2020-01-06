package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnEnterInStateBuilder<S, A>(
    internal val flatMapPolicy: FlatMapPolicy
) : InStateSideEffectBuilder<S, A>() {

    override fun generateSideEffect(): SideEffect<S, Action<S, A>> {
        val mutex = Mutex()

        return { actions: Flow<Action<S, A>>, state: StateAccessor<S> ->
            actions
        }
    }
}
