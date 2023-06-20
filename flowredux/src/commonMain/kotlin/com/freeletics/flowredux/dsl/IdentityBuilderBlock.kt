package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffect
import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class IdentityBuilderBlock<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
    private val identity: (InputState) -> Any,
) : BaseBuilderBlock<InputState, S, A>() {

    @Suppress("UNCHECKED_CAST")
    override fun sideEffectIsInState(initialState: InputState) = SideEffect.IsInState<S> {
        isInState.check(it) && identity(initialState) == identity(it as InputState)
    }
}
