package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.sideeffects.reduxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@ExperimentalCoroutinesApi
public abstract class FlowReduxStateMachineFactory<S : Any, A : Any>() {
    internal lateinit var stateHolder: StateHolder<S>
    internal lateinit var sideEffectBuilders: List<SideEffectBuilder<*, S, A>>

    protected fun initializeWithOnEachLaunch(initialState: S) {
        stateHolder = LossyStateHolder({ initialState })
    }

    protected fun initializeWithOnEachLaunch(initialState: () -> S) {
        stateHolder = LossyStateHolder(initialState)
    }

    protected fun initializeWith(initialState: S, reuseLastEmittedStatedOnLaunch: Boolean = true) {
        stateHolder = if (reuseLastEmittedStatedOnLaunch) {
            InMemoryStateHolder({ initialState })
        } else {
            LossyStateHolder({ initialState })
        }
    }

    protected fun initializeWith(reuseLastEmittedStatedOnLaunch: Boolean = true, initialState: () -> S) {
        stateHolder = if (reuseLastEmittedStatedOnLaunch) {
            InMemoryStateHolder(initialState)
        } else {
            LossyStateHolder(initialState)
        }
    }

    protected fun spec(specBlock: FlowReduxBuilder<S, A>.() -> Unit) {
        check(!::sideEffectBuilders.isInitialized) {
            "State machine spec has already been set. It's only allowed to call spec {...} once."
        }
        sideEffectBuilders = FlowReduxBuilder<S, A>().apply(specBlock).sideEffectBuilders
    }

    public fun launchIn(scope: CoroutineScope): FlowReduxStateMachine<StateFlow<S>, A> {
        checkInitialized()

        val inputActions = Channel<A>(Channel.BUFFERED)
        val initialState = stateHolder.getState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders)
            .onEach { stateHolder.saveState(it) }
            .stateIn(scope, SharingStarted.Lazily, initialState)

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
