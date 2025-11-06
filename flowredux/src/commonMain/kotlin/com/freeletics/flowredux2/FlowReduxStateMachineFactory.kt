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

/**
 * Used to define a state machine using [initializeWith] and [spec]. It can then be started with methods like [launchIn] or
 * [shareIn].
 *
 * Example:
 * ```
 * init {
 *     initializeWith { Loading }
 *
 *     spec {
 *         inState<Loading> {
 *             onEnter {
 *                 when(val result = loadData() {
 *                     is Success -> override { Content(result.data) }
 *                     is Error -> override { Error(result.errorMessage) }
 *                 }
 *             }
 *         }
 *
 *         inState<Content> {
 *             // ...
 *         }
 *
 *         inState<Error> {
 *              on<RetryClicked> {
 *                  override { Loading }
 *              }
 *         }
 *     }
 * }
 * ```
 */
public abstract class FlowReduxStateMachineFactory<S : Any, A : Any>() {
    // exposed internally for testing where RENDEZVOUS results in more predictable tests
    internal open val actionChannelCapacity = Channel.BUFFERED

    internal lateinit var stateHolder: StateHolder<S>
    internal lateinit var sideEffectBuilders: List<SideEffectBuilder<*, S, A>>

    /**
     * Define the behavior of this state machine. This is done by defining [FlowReduxBuilder.inState]
     * blocks which in turn can handle received actions, collect flows and perform other operations.
     *
     * Note: It's only possible to call this method once.
     */
    @ExperimentalCoroutinesApi
    protected fun spec(specBlock: FlowReduxBuilder<S, A>.() -> Unit) {
        check(!::sideEffectBuilders.isInitialized) {
            "State machine spec has already been set. It's only allowed to call spec {...} once."
        }
        sideEffectBuilders = FlowReduxBuilder<S, A>().apply(specBlock).sideEffectBuilders
    }

    /**
     * Create and start running a [FlowReduxStateMachine] that exposes a [StateFlow]. The state machine
     * will stay active as long as the given [scope] is not cancelled.
     *
     * Note: [initializeWith] and [spec] need to be called before this method.
     */
    public fun launchIn(scope: CoroutineScope): FlowReduxStateMachine<StateFlow<S>, A> {
        checkInitialized()

        val inputActions = Channel<A>(actionChannelCapacity)
        val initialState = stateHolder.getState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders)
            .onEach { stateHolder.saveState(it) }
            .stateIn(scope, SharingStarted.Lazily, initialState)

        return FlowReduxStateMachine(state, inputActions, scope)
    }

    /**
     * Create and start running a [FlowReduxStateMachine] that exposes a [SharedFlow]. The state machine
     * will stay active as long as the given [scope] is not cancelled.
     *
     * This variation is useful for tests where the value conflation of [StateFlow] can lead to flakiness.
     *
     * Note: [initializeWith] and [spec] need to be called before this method.
     */
    public fun shareIn(scope: CoroutineScope): FlowReduxStateMachine<SharedFlow<S>, A> {
        checkInitialized()

        val inputActions = Channel<A>(actionChannelCapacity)
        val initialState = stateHolder.getState()
        val state = inputActions
            .receiveAsFlow()
            .reduxStore(initialState, sideEffectBuilders)
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

/**
 * Sets the initial state for the state machine.
 *
 * When [reuseLastEmittedStateOnLaunch] is `true` [initialState] will only be called the first time a state machine is launched. Subsequent launches
 * will start using the last emitted value by a launched state machine. When the multiple state machines from the same factory instance are launched
 * in parallel the state of whichever state machine emitted last will be used.
 *
 * [reuseLastEmittedStateOnLaunch] being `false` will result in [initialState] being called for every launch.
 */
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
