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

    private var specBlockSet = false
    private var specBlock: (FlowReduxStoreBuilder<S, A>.() -> Unit)? = null

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (specBlockSet) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once."
            )
        }
        this.specBlock = specBlock
        this.specBlockSet = true
    }

    private val inputActionChannel = Channel<A>(Channel.UNLIMITED)

    suspend fun dispatch(action: A) {
        inputActionChannel.send(action)
    }

    val state: Flow<S> by lazy(LazyThreadSafetyMode.NONE) {
        val spec = specBlock
        println("Spec is $spec")
           if (spec == null) {
               throw IllegalStateException(
                   """
                        No state machine specs are defined. Did you call spec { ... } in init {...}?
                        Example usage:

                        class MyStateMachine : FlowReduxStateMachine<State, Action>(InitialState) {

                            init{
                                spec {
                                    inState<FooState> {
                                        on<BarAction> { ... }
                                    }
                                    ...
                                }
                            }
                        }
                    """.trimIndent()
               )
           }
        inputActionChannel
            .consumeAsFlow()
            .reduxStore<S, A>(logger, initialStateSupplier, spec)
            .also {
                specBlock = null // Free up memory
            }
    }
}
