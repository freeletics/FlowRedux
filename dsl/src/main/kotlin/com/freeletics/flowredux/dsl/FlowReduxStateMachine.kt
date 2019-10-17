package com.freeletics.flowredux.dsl

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

open class FlowReduxStateMachine<S : Any, A : Any>(
    initialStateSupplier: () -> S,
    builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
) {

    constructor(
        initialState: S,
        builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
    ) : this({ initialState }, builderBlock)

    private val inputActionChannel = Channel<A>(Channel.UNLIMITED)

    suspend fun dispatch(action: A) {
        inputActionChannel.send(action)
    }

    val state: Flow<S> = inputActionChannel
        .consumeAsFlow()
        .reduxStore<S, A>(initialStateSupplier, builderBlock)
}