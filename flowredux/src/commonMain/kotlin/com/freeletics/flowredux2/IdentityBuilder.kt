package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffect
import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.util.FlowReduxDsl
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class IdentityBuilder<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
    private val identity: (InputState) -> Any?,
    override val logger: TaggedLogger?,
) : BaseBuilder<InputState, S, A>() {
    @Suppress("UNCHECKED_CAST")
    override fun sideEffectIsInState(initialState: InputState) = SideEffect.IsInState<S> {
        isInState.check(it) && identity(initialState) == identity(it as InputState)
    }
}
