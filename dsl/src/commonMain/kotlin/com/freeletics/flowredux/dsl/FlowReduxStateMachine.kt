package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

abstract class FlowReduxStateMachine<S : Any, A : Any>(
    logger: FlowReduxLogger?,
    initialStateSupplier: () -> S
) {

    // TODO remove constructor overloads
    constructor(
        initialStateSupplier: () -> S
    ) : this(
        logger = null,
        initialStateSupplier = initialStateSupplier
    )

    constructor(initialState: S) : this(logger = null, initialState = initialState)

    constructor(
        logger: FlowReduxLogger?,
        initialState: S
    ) : this(logger, { initialState })

    protected abstract val spec: FlowReduxStoreBuilder<S, A>.() -> Unit

    private val inputActionChannel = Channel<A>(Channel.UNLIMITED)

    suspend fun dispatch(action: A) {
        inputActionChannel.send(action)
    }

    val state: Flow<S> by lazy(LazyThreadSafetyMode.NONE) {
        inputActionChannel
            .consumeAsFlow()
            .reduxStore<S, A>(logger, initialStateSupplier, spec)
    }
}

