package com.freeletics.flowredux.dsl

internal sealed class Action<S, A>

/**
 *
 */
sealed class ChangeState<out S>

/**
 * Sets a new state by directly override any previous state
 */
data class SetState<S>(val newState: S) : ChangeState<S>() // TODO make val internal

/**
 * If you want to change the state by copying the old state
 */
class CopyStateWith<S>(val reducer: S.() -> S) : ChangeState<S>() // TODO make val internal

/**
 * No change, this is semantially equivalent to use [SetState] and pass in the previous state
 */
object NoStateChange : ChangeState<Nothing>()

fun <S> ChangeState<S>.reduce(state: S): S {
    return when (val change = this) {
        is SetState -> change.newState
        is NoStateChange -> state // TODO throw exception instead?
        is CopyStateWith -> change.reducer(state)
    }
}


internal data class ChangeStateAction<S, A>(
    private val loggingInfo: String,
    internal val runReduceOnlyIf: (S) -> Boolean,
    internal val changeState: ChangeState<S>
) : Action<S, A>() {
    override fun toString(): String {
        return "SetStateAction $loggingInfo"
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
        is ChangeStateAction<S, A> ->
            if (action.runReduceOnlyIf.invoke(state))
                action.changeState.reduce(state)
            else state
        is ExternalWrappedAction<S, A> -> state
        is InitialStateAction<S, A> -> state
    }