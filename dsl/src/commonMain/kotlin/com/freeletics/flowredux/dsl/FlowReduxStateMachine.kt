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
    private var initialStateSupplier: () -> S,
    private val logger: FlowReduxLogger? = null
) {

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    constructor(initialState: S, logger: FlowReduxLogger? = null) : this(
        logger = logger,
        initialStateSupplier = { initialState })

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (::internalState.isInitialized)
            throw IllegalStateException(
                "State machine spec has already been set. " +
                        "It's only allowed to call spec {...} once."
            )
            
        outputState = inputActions
            .consumeAsFlow()
            .reduxStore(logger, initialStateSupplier, specBlock)

    }

    val state: Flow<S> 
        get() {
            checkSpecBlockSet()
            return outputState
        }
        
    suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        inputActions.send(action)
    }

    private fun checkSpecBlockSet() {
        if (!::internalState.isInitialized) {
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
