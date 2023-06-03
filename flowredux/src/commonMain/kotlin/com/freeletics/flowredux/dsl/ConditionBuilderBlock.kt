package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class ConditionBuilderBlock<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
) : BaseBuilderBlock<InputState, S, A>() {

    public fun untilIdentityChanges(
        identity: (InputState) -> Any,
        block: IdentityBuilderBlock<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += IdentityBuilderBlock<InputState, S, A>(isInState, identity)
            .apply(block)
            .sideEffectBuilders
    }
}
