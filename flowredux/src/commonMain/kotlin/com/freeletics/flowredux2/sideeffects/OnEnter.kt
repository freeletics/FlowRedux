package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.logI
import com.freeletics.flowredux2.logV
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

@ExperimentalCoroutinesApi
internal class OnEnter<InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val initialState: InputState,
    private val handler: suspend ChangeableState<InputState>.() -> ChangedState<S>,
    override val logger: TaggedLogger?,
) : SideEffect<InputState, S, A>() {
    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return flow {
            logger.logI { "Running" }
            emit(handler(ChangeableState(initialState)))
        }
    }
}
