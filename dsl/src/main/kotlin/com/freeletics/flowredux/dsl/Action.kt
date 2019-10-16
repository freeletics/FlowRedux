package com.freeletics.flowredux.dsl

internal sealed class Action<S, A>
internal data class SelfReducableAction<S, A>(val reduce: (S) -> S) : Action<S, A>()
internal data class ExternalWrappedAction<S, A>(val action: A) : Action<S, A>()
internal class InitialStateAction<S, A> : Action<S, A>()

// TODO we may need an internal action to propagate state changes. This is useful to trigger things
//  like observe a database in a certain state only. We need such a state to propagate the initial
//  State and to dispose database flows once a new stat has been reached

internal fun <S : Any, A> reducer(state: S, action: Action<S, A>): S =
    when (action) {
        is SelfReducableAction<S, A> -> action.reduce.invoke(state)
        is ExternalWrappedAction<S, A> -> state
        is InitialStateAction<S, A> -> state
    }