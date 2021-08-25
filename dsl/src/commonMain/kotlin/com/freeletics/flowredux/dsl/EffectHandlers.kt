package com.freeletics.flowredux.dsl

typealias OnActionEffectHandler<InputState, A> = suspend (action: A, state: InputState) -> Unit
typealias OnEnterStateEffectHandler<InputState> = suspend (state: InputState) -> Unit
typealias CollectFlowEffectHandler<T, InputState> = suspend (value: T, state: InputState) -> Unit
