package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.State
import com.freeletics.flowredux.util.mapToIsInState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * A builder that generates a [SideEffect] that triggers every time the state machine enters
 * a certain state.
 */
@ExperimentalCoroutinesApi
internal class OnEnterInStateSideEffectBuilder<InputState : S, S : Any, A : Any>(
    override val isInState: IsInState<S>,
    private val handler: suspend (state: State<InputState>) -> ChangedState<S>,
) : InStateSideEffectBuilder<InputState, S, A>() {

    override fun generateSideEffect(): SideEffect<S, A> {
        return { actions: Flow<Action<S, A>>, getState: GetState<S> ->
            actions
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
}
