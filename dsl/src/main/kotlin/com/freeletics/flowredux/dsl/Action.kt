package com.freeletics.flowredux.dsl

internal sealed class Action<S, A>
internal data class SelfReducableAction<S, A>(
    private val loggingInfo: String,
    val reduce: (S) -> S
) : Action<S, A>() {
    override fun toString(): String {
        return "SelfReducableAction $loggingInfo"
    }
}

internal data class ExternalWrappedAction<S, A>(internal val action: A) : Action<S, A>() {
    override fun toString(): String {
        return action.toString()
    }
}

internal class InitialStateAction<S, A> : Action<S, A>() {
    override fun toString(): String {
        return "InitialStateDispatched"
    }
}

// TODO maybe we need something like an internal State that also holds the information if we have
//  to propagate a state change to the outside of id a ExternalWrappedAction caused emitting the
//  same state again. For now we solve this with an distinctUntilChanged() backed in

internal fun <S : Any, A> reducer(state: S, action: Action<S, A>): S =
    when (action) {
        is SelfReducableAction<S, A> -> action.reduce.invoke(state)
        is ExternalWrappedAction<S, A> -> state
        is InitialStateAction<S, A> -> state
    }