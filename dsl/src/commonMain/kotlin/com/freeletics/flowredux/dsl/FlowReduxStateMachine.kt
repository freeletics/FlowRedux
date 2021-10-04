package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

@FlowPreview
@ExperimentalCoroutinesApi
abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private val initialStateSupplier: () -> S,
    private val logger: FlowReduxLogger? = null
) {

    private val inputActions = Channel<A>()
    private var specBlock: (FlowReduxStoreBuilder<S, A>.() -> Unit)? = null
    private var specBlockSet = false

    constructor(
        initialStateSupplier: () -> S
    ) : this(
        logger = null,
        initialStateSupplier = initialStateSupplier
    )

    constructor(initialState: S) : this(logger = null, initialState = initialState)

    constructor(
        initialState: S,
        logger: FlowReduxLogger?
    ) : this(logger = logger, initialStateSupplier = { initialState })

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {

        if (this.specBlock != null)
            throw IllegalStateException(
                "State machine spec has already been set. " +
                        "It's only allowed to call spec {...} once."
            )

        this.specBlock = specBlock
        this.specBlockSet = true

    }

    val state: Flow<S> by lazy(LazyThreadSafetyMode.NONE) {
        checkSpecBlockSet()
        inputActions
            .consumeAsFlow()
            .reduxStore(logger, initialStateSupplier, specBlock!!)
            .also {
                specBlock = null // Free up memory
            }
    }

    suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        inputActions.send(action)
    }

    private fun checkSpecBlockSet() {
        if (!specBlockSet) {
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
    }
}
