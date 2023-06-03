package com.freeletics.flowredux.sideeffects

internal sealed class Action<A>

internal data class ExternalWrappedAction<A>(internal val action: A) : Action<A>() {
    override fun toString(): String {
        return action.toString()
    }
}

internal class InitialStateAction<A> : Action<A>() {
    override fun toString(): String {
        return "InitialStateDispatched"
    }
}
