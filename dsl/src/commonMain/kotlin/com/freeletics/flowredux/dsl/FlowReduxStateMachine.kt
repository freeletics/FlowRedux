package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow

@FlowPreview
@ExperimentalCoroutinesApi
abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private var initialStateSupplier: () -> S,
    private val logger: FlowReduxLogger? = null
) : StateMachine<S, A> {

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    constructor(initialState: S, logger: FlowReduxLogger? = null) : this(
        logger = logger,
        initialStateSupplier = { initialState })

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (::outputState.isInitialized) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                        "It's only allowed to call spec {...} once."
            )
        }
            
        outputState = inputActions
            .receiveAsFlow()
            .reduxStore(logger, initialStateSupplier, specBlock)
    }

    override val state: Flow<S> 
        get() {
            checkSpecBlockSet()
            return outputState
        }
        
    override suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        inputActions.send(action)
    }

    private fun checkSpecBlockSet() {
        if (!::outputState.isInitialized) {
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
