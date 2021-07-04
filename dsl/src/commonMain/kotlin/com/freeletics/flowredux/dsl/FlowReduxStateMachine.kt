package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private val initialState: S,
    private val scope: CoroutineScope,
    private val logger: FlowReduxLogger? = null
) : StateMachine<S, A> {

    private val inputActions = MutableSharedFlow<A>()
    private val internalState = MutableStateFlow(initialState)

    private var specBlockSet = false

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (specBlockSet) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once."
            )
        }
        this.specBlockSet = true
        scope.launch {
            inputActions.reduxStore(logger, initialStateSupplier = { initialState }, specBlock)
                .collect(internalState::emit)
        }
    }

    override val state: StateFlow<S> get() {
        check(scope.isActive) { "The scope of this state machine was already cancelled." }
        checkSpecBlockSet()
        return internalState
    }

    override suspend fun dispatch(action: A) {
        check(scope.isActive) { "The scope of this state machine was already cancelled." }
        inputActions.emit(action)
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
