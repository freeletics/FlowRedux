package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.sideeffects.reduxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

public abstract class FlowReduxStateMachineFactory<S : Any, A : Any>() {
    // exposed internally for testing where RENDEZVOUS results in more predictable tests
    internal open val actionChannelCapacity = Channel.BUFFERED

    internal lateinit var stateHolder: StateHolder<S>
    internal lateinit var sideEffectBuilders: List<SideEffectBuilder<*, S, A>>

    internal var logger: TaggedLogger? = null

    /**
     * Install a [logger] to observe actions in a [FlowReduxStateMachine] produced by this
     * factory.
     */
    public fun installLogger(logger: Logger, name: String = this::class.simpleName!!) {
        check(!::sideEffectBuilders.isInitialized) {
            "State machine spec has already been set. Logger should be installed before."
        }
        this.logger = TaggedLogger(logger, name)
    }

    @ExperimentalCoroutinesApi
    protected fun spec(specBlock: FlowReduxBuilder<S, A>.() -> Unit) {
        check(!::sideEffectBuilders.isInitialized) {
            "State machine spec has already been set. It's only allowed to call spec {...} once."
        }
        sideEffectBuilders = FlowReduxBuilder<S, A>(logger).apply(specBlock).sideEffectBuilders
    }

    public fun launchIn(scope: CoroutineScope): FlowReduxStateMachine<StateFlow<S>, A> {
        checkInitialized()

        val inputActions = Channel<A>(actionChannelCapacity)
        val initialState = stateHolder.getState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders, logger)
            .onEach { stateHolder.saveState(it) }
            .stateIn(scope, SharingStarted.Lazily, initialState)

        return FlowReduxStateMachine(state, inputActions, scope)
    }

    public fun shareIn(scope: CoroutineScope): FlowReduxStateMachine<SharedFlow<S>, A> {
        checkInitialized()

        val inputActions = Channel<A>(actionChannelCapacity)
        val initialState = stateHolder.getState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders, logger)
            .onEach { stateHolder.saveState(it) }
            .shareIn(scope, SharingStarted.Lazily, replay = 1)

        return FlowReduxStateMachine(state, inputActions, scope)
    }

    internal fun checkInitialized() {
        check(::stateHolder.isInitialized) {
            """
            No initial state for the state machine was specified, did you call one of the initializeWith()
            methods?

            Example usage:
            class MyStateMachine : FlowReduxStateMachineFactory<State, Action>() {
                init{
                    initializeWith(InitialState)
                    spec {
                        ...
                    }
                }
            }
            """.trimIndent()
        }
        check(::sideEffectBuilders.isInitialized) {
            """
            No state machine specs are defined. Did you call spec { ... } in init {...}?
            Example usage:

            class MyStateMachine : FlowReduxStateMachineFactory<State, Action>() {

                init{
                    initializeWith(...)
                    spec {
                        inState<FooState> {
                            on<BarAction> { ... }
                        }
                        ...
                    }
                }
            }
            """.trimIndent()
        }
    }
}

public fun <S : Any> FlowReduxStateMachineFactory<S, *>.initializeWith(reuseLastEmittedStateOnLaunch: Boolean = true, initialState: () -> S) {
    stateHolder = if (reuseLastEmittedStateOnLaunch) {
        InMemoryStateHolder(initialState)
    } else {
        LossyStateHolder(initialState)
    }
}

internal abstract class StateHolder<S : Any> internal constructor() {
    internal abstract fun getState(): S

    internal abstract fun saveState(s: S)
}

internal class LossyStateHolder<S : Any>(
    private val initialState: () -> S,
) : StateHolder<S>() {
    override fun getState(): S = initialState()

    override fun saveState(s: S) {}
}

private class InMemoryStateHolder<S : Any>(
    private val initialState: () -> S,
) : StateHolder<S>() {
    private var state: S? = null

    override fun getState(): S {
        return state ?: initialState().also { state = it }
    }

    override fun saveState(s: S) {
        state = s
    }
}
