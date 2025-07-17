package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.sideeffects.reduxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@ExperimentalCoroutinesApi
public abstract class FlowReduxStateMachineFactory<S : Any, A : Any>(
    internal val initialState: () -> S,
) {
    public constructor(initialState: S) : this({ initialState })

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
        val initialState = initialState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders)
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
}
