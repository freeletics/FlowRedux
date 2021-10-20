package com.freeletics.flowredux.dsl

import kotlinx.coroutines.flow.Flow

fun interface FlowBuilder<InputState, T> {
    fun build(state: Flow<InputState>): Flow<T>
}
