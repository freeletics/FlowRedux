package com.freeletics.flowredux.dsl

import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class InStateBuilderBlock<InputState : S, S : Any, A : Any> internal constructor(
    override val isInState: (S) -> Boolean,
) : BaseBuilderBlock<InputState, S, A>()
