package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.mapToIsInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

@ExperimentalCoroutinesApi
internal class OnEnter<InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val handler: suspend (state: State<InputState>) -> ChangedState<S>,
) : LegacySideEffect<InputState, S, A>() {

    override fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>> {
        return actions
            .mapToIsInState(isInState, getState)
            .flatMapLatest {
                if (it) {
                    changeState(getState) { snapshot ->
                        handler(State(snapshot))
                    }
                } else {
                    emptyFlow()
                }
            }
    }
}
