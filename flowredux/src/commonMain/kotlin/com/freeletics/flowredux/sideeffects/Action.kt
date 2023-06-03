package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.reduce

internal sealed class Action<S, A>

internal data class ChangeStateAction<S, A>(
    private val runReduceOnlyIf: InStateSideEffectBuilder.IsInState<S>,
    private val changedState: ChangedState<S>,
) : Action<S, A>() {
    fun reduce(state: S): S {
        if (runReduceOnlyIf.check(state)) {
            return changedState.reduce(state)
        }
        return state
    }

    override fun toString(): String {
        return "SetStateAction"
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
