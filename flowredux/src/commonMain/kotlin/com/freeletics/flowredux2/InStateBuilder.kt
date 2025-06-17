package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.util.FlowReduxDsl
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class InStateBuilder<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<S>,
) : BaseBuilder<InputState, S, A>() {
    /**
     * Allows handling certain actions or events only while an extra condition is `true`
     * for the current state.
     */
    public fun condition(
        condition: (InputState) -> Boolean,
        block: ConditionBuilder<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += ConditionBuilder<InputState, S, A> {
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
        block: IdentityBuilder<InputState, S, A>.() -> Unit,
    ) {
        sideEffectBuilders += IdentityBuilder<InputState, S, A>(isInState, identity)
            .apply(block)
            .sideEffectBuilders
    }
}
