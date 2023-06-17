package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ExperimentalCoroutinesApi
internal class OnEnter<InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val initialState: InputState,
    private val handler: suspend (state: State<InputState>) -> ChangedState<S>,
) : SideEffect<InputState, S, A>() {

    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return flow {
            emit(handler(State(initialState)))
        }
    }
}
