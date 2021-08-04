package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

@FlowPreview
@ExperimentalCoroutinesApi
abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private val initialState: S,
    private val scope: CoroutineScope,
    private val logger: FlowReduxLogger? = null
) : StateMachine<S, A> {

    private val inputActions = Channel<A>()
    private var internalState: StateFlow<S>? = null

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (internalState != null) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once."
            )
        }
        internalState = inputActions
            .consumeAsFlow()
            .reduxStore(logger, initialState, specBlock)
            .stateIn(scope, SharingStarted.Lazily, initialState)
    }

    override val state: StateFlow<S> get() {
        check(scope.isActive) { "The scope of this state machine was already cancelled." }
        checkSpecBlockSet()
        return internalState!!
    }

    override suspend fun dispatch(action: A) {
        check(scope.isActive) { "The scope of this state machine was already cancelled." }
        inputActions.send(action)
    }

    private fun checkSpecBlockSet() {
       if (internalState == null) {
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
