package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.reduxStore
import com.freeletics.flowredux2.util.AtomicCounter
import com.freeletics.khonshu.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

@ExperimentalCoroutinesApi
public abstract class LegacyFlowReduxStateMachine<S : Any, A : Any>(
    private val initialStateSupplier: () -> S,
) : StateMachine<S, A> {
    public constructor(initialState: S) : this(initialStateSupplier = { initialState })

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    private val activeFlowCounter = AtomicCounter(0)

    protected fun spec(specBlock: FlowReduxBuilder<S, A>.() -> Unit) {
        if (::outputState.isInitialized) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once.",
            )
        }

        val sideEffectBuilders = FlowReduxBuilder<S, A>().apply(specBlock).sideEffectBuilders

        outputState = inputActions
            .receiveAsFlow()
            .reduxStore(initialStateSupplier(), sideEffectBuilders)
            .onStart {
                if (activeFlowCounter.incrementAndGet() > 1) {
                    throw IllegalStateException(
                        "Can not collect state more than once at the same time. Make sure the" +
                            "previous collection is cancelled before starting a new one. " +
                            "Collecting state in parallel would lead to subtle bugs.",
                    )
                }
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
                    "Start collecting the state Flow before dispatching any action.",
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
                """.trimIndent(),
            )
        }
    }
}
