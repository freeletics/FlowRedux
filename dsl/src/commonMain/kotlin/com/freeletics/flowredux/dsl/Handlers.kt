package com.freeletics.flowredux.dsl

public fun interface OnActionHandler<InputState, S, A> {
    public suspend fun handle(action: A, state: InputState): ChangeState<S>
}

public fun interface OnActionEffectHandler<InputState, A> {
    public suspend fun handle(action: A, state: InputState)
}

public fun interface OnEnterHandler<InputState, S> {
    public suspend fun handle(state: InputState): ChangeState<S>
}

public fun interface OnEnterStateEffectHandler<InputState> {
    public suspend fun handle(state: InputState)
}

public fun interface CollectFlowHandler<T, InputState, S> {
    public suspend fun handle(value: T, state: InputState): ChangeState<S>
}

public fun interface CollectFlowEffectHandler<T, InputState> {
    public suspend fun handle(value: T, state: InputState)
}
