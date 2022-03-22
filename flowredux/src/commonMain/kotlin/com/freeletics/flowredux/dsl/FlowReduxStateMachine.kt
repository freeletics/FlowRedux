package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.dsl.util.AtomicCounter
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

@FlowPreview
@ExperimentalCoroutinesApi
public abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private val initialStateSupplier: () -> S,
) : StateMachine<S, A> {

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    private val activeFlowCounter = AtomicCounter(0)

    public constructor(initialState: S) : this(initialStateSupplier = { initialState })

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (::outputState.isInitialized) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once."
            )
        }

        outputState = inputActions
            .receiveAsFlow()
            .reduxStore(initialStateSupplier, specBlock)
            .onStart {
                activeFlowCounter.incrementAndGet()
            }
            .onCompletion {
                activeFlowCounter.decrementAndGet()
            }
    }

    override val state: Flow<S>
        get() {
            checkSpecBlockSet()
            return outputState
        }

    override suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        if (activeFlowCounter.get() <= 0) {
            throw IllegalStateException(
                "Cannot dispatch action $action because state Flow of this " +
                    "FlowReduxStateMachine is not collected yet. " +
                    "Start collecting the state Flow before dispatching any action."
            )
        }
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
