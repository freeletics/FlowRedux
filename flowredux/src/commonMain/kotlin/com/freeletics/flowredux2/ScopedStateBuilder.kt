package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.sideeffects.inScopedState
import com.freeletics.flowredux2.util.FlowReduxDsl
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Defines behavior that can only access and mutate a scoped part of a state.
 */
@ExperimentalCoroutinesApi
@FlowReduxDsl
public class ScopedStateBuilder<ScopedState : Any, A : Any> internal constructor(
    override val isInState: SideEffectBuilder.IsInState<ScopedState>,
    override val logger: TaggedLogger?,
) : BaseBuilder<ScopedState, ScopedState, A>() {
    /**
     * Allows handling actions or events only while an extra condition is `true` for the scoped state.
     */
    public fun condition(
        condition: (ScopedState) -> Boolean,
        name: String? = null,
        block: ConditionBuilder<ScopedState, ScopedState, A>.() -> Unit,
    ) {
        sideEffectBuilders += ConditionBuilder<ScopedState, ScopedState, A>(
            isInState = { isInState.check(it) && condition(it) },
            logger = logger?.wrap("condition<${name ?: "?"}>"),
        ).apply(block).sideEffectBuilders
    }

    /**
     * Runs the block while the selected identity of the scoped state remains unchanged.
     */
    public fun untilIdentityChanges(
        identity: (ScopedState) -> Any?,
        name: String? = null,
        block: IdentityBuilder<ScopedState, ScopedState, A>.() -> Unit,
    ) {
        sideEffectBuilders += IdentityBuilder<ScopedState, ScopedState, A>(
            isInState = isInState,
            identity = identity,
            logger = logger?.wrap("untilIdentityChanges<${name ?: "?"}>"),
        ).apply(block).sideEffectBuilders
    }
}

/**
 * Defines behavior that can only access and mutate the state selected by [get]. Scoped mutations
 * are applied through [set] to the latest root state.
 */
@ExperimentalCoroutinesApi
public fun <InputState : S, S : Any, A : Any, ScopedState : Any> InStateBuilder<InputState, S, A>.inScopedState(
    get: (InputState) -> ScopedState,
    set: InputState.(ScopedState) -> InputState,
    block: ScopedStateBuilder<ScopedState, A>.() -> Unit,
) {
    val scopedBuilders = ScopedStateBuilder<ScopedState, A>(
        isInState = { true },
        logger = logger?.wrap("inScopedState"),
    ).apply(block).sideEffectBuilders

    sideEffectBuilders += scopedBuilders.map {
        it.inScopedState(isInState, get, set)
    }
}
