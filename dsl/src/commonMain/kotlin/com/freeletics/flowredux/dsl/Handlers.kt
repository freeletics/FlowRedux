package com.freeletics.flowredux.dsl

fun interface OnActionHandler<InputState, S, A> {
    suspend fun handle(action: A, state: InputState): ChangeState<S>
}

fun interface OnActionEffectHandler<InputState, A> {
    suspend fun handle(action: A, state: InputState)
}

fun interface OnEnterHandler<InputState, S> {
    suspend fun handle(state: InputState): ChangeState<S>
}

fun interface OnEnterStateEffectHandler<InputState> {
    suspend fun handle(state: InputState)
}

fun interface CollectFlowHandler<T, InputState, S> {
    suspend fun handle(value: T, state: InputState): ChangeState<S>
}

fun interface CollectFlowEffectHandler<T, InputState> {
    suspend fun handle(value: T, state: InputState)
}
