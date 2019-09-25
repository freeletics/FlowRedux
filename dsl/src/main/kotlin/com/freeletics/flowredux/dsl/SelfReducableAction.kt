package com.freeletics.flowredux.dsl

internal sealed class Action<A>
internal class SelfReducableAction<A>(val reduce: (Any) -> Any) : Action<A>()
internal class ExternalWrappedAction<A>(val action: A) : Action<A>()

// TODO we may need an internal action to propagate state changes. This is useful to trigger things
//  like observe a database in a certain state only. We need such a state to propagate the initial
//  State and to dispose database flows once a new stat has been reached

internal fun <S : Any, A> reducer(state: S, action: Action<A>): S =
    when (action) {
        is SelfReducableAction<A> -> action.reduce.invoke(state) as S
        is ExternalWrappedAction<A> -> state
    }