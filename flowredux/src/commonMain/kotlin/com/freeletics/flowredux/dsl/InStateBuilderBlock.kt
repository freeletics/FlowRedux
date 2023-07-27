package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class InStateBuilderBlock<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
) : BaseBuilderBlock<InputState, S, A>() {

    /**
     * Allows handling certain actions or events only while an extra condition is `true`
     * for the current state.
     */
    public fun condition(
        condition: (InputState) -> Boolean,
        block: ConditionBuilderBlock<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += ConditionBuilderBlock<InputState, S, A> {
            @Suppress("UNCHECKED_CAST")
            isInState.check(it) && condition(it as InputState)
        }.apply(block).sideEffectBuilders
    }

    /**
     * Anything inside this block will only run while the [identity] of the current state
     * remains the same. The `identity` is determined by the given function and uses
     * [equals] for the comparison.
     *
     * When the `identity` changes anything currently running in the block is cancelled.
     * Afterwards a the blocks are started again for the new `identity`.
     */
    public fun untilIdentityChanges(
        identity: (InputState) -> Any?,
        block: IdentityBuilderBlock<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += IdentityBuilderBlock<InputState, S, A>(isInState, identity)
            .apply(block)
            .sideEffectBuilders
    }
}
