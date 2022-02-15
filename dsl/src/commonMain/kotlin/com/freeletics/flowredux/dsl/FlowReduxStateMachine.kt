package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@FlowPreview
@ExperimentalCoroutinesApi
public abstract class FlowReduxStateMachine<S : Any, A : Any>(
    initialStateSupplier: () -> S,
    private val logger: FlowReduxLogger? = null
) : StateMachine<S, A> {

    public val initialState: S by lazy(LazyThreadSafetyMode.NONE, initialStateSupplier)

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    private val activeFlowCounterMutex = Mutex()
    private var activeFlowCounter = 0

    public constructor(initialState: S, logger: FlowReduxLogger? = null) : this(
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
            .reduxStore(logger, initialState, specBlock)
            .onStart {
                activeFlowCounterMutex.withLock {
                    activeFlowCounter++
                }
            }
            .onCompletion {
                activeFlowCounterMutex.withLock {
                    activeFlowCounter--
                }
            }
    }

    override val state: Flow<S>
        get() {
            checkSpecBlockSet()
            return outputState
        }

    override suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        activeFlowCounterMutex.withLock {
            if (activeFlowCounter <= 0) {
                throw IllegalStateException(
                    "Cannot dispatch action $action because state Flow of this " +
                            "FlowReduxStateMachine is not collected yet. " +
                            "Start collecting the state Flow before dispatching any action."
                )
            }
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
