package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

open class FlowReduxStateMachine<S : Any, A : Any>(
    logger: FlowReduxLogger?,
    initialStateSupplier: () -> S,
    builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
) {

    // TODO remove constructor overloads
    constructor(
        initialStateSupplier: () -> S,
        builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
    ) : this(
        logger = null,
        initialStateSupplier = initialStateSupplier,
        builderBlock = builderBlock
    )

    constructor(
        initialState: S,
        builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
    ) : this(logger = null, initialState = initialState, builderBlock = builderBlock)

    constructor(
        logger: FlowReduxLogger?,
        initialState: S,
        builderBlock: FlowReduxStoreBuilder<S, A>.() -> Unit
    ) : this(logger, { initialState }, builderBlock)

    private val inputActionChannel = Channel<A>(Channel.UNLIMITED)

    suspend fun dispatch(action: A) {
        inputActionChannel.send(action)
    }

    val state: Flow<S> = inputActionChannel
        .consumeAsFlow()
        .reduxStore<S, A>(logger, initialStateSupplier, builderBlock)
}