package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class InStateBuilderBlock<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
) : BaseBuilderBlock<InputState, S, A>() {

    public fun condition(
        condition: (InputState) -> Boolean,
        block: ConditionBuilderBlock<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += ConditionBuilderBlock<InputState, S, A> {
            @Suppress("UNCHECKED_CAST")
            isInState.check(it) && condition(it as InputState)
        }.apply(block).sideEffectBuilders
    }
}
