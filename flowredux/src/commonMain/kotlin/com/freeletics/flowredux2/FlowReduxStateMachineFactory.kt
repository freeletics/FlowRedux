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
public abstract class FlowReduxStateMachineFactory<S : Any, A : Any>(
    internal val stateHolder: StateHolder<S>,
) {
    public constructor(state: S) : this(inMemoryStateHolder(state))
    public constructor(stateSupplier: () -> S) : this(inMemoryStateHolder(stateSupplier))

    internal lateinit var sideEffectBuilders: List<SideEffectBuilder<*, S, A>>

    protected fun spec(specBlock: FlowReduxBuilder<S, A>.() -> Unit) {
        if (::sideEffectBuilders.isInitialized) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once.",
            )
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
        check(!::sideEffectBuilders.isInitialized) {
            """
            No state machine specs are defined. Did you call spec { ... } in init {...}?
            Example usage:

            class MyStateMachine : FlowReduxStateMachineFactory<State, Action>(InitialState) {

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
        }
    }

    public companion object {
        public fun <S : Any> lossyStateHolder(initialState: S): StateHolder<S> {
            return LossyStateHolder({ initialState })
        }

        public fun <S : Any> lossyStateHolder(initialState: () -> S): StateHolder<S> {
            return LossyStateHolder(initialState)
        }

        public fun <S : Any> inMemoryStateHolder(initialState: S): StateHolder<S> {
            return InMemoryStateHolder({ initialState })
        }

        public fun <S : Any> inMemoryStateHolder(initialState: () -> S): StateHolder<S> {
            return InMemoryStateHolder(initialState)
        }
    }
}

public abstract class StateHolder<S : Any> internal constructor() {
    internal abstract fun getState(): S

    internal abstract fun saveState(s: S)
}

private class LossyStateHolder<S : Any>(
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
