package com.freeletics.flowredux.dsl

/**
 * Represents a state transition. Instances of this a created through the [State.mutate],
 * [State.override] and [State.noChange] methods.
 *
 * [ChangedState] doesnt allow you to directly access any property.
 * Then you may wonder how do you write unit test for one of your functions that return a [ChangedState]?
 * You need to call [ChangedState.reduce] to get the actual result of the change state.
 */
public sealed class ChangedState<out S>

internal class UnsafeMutateState<InputState, S>(
    internal val reducer: InputState.() -> S
) : ChangedState<S>() {
    @Suppress("UNCHECKED_CAST")
    internal fun reduceImpl(state: S): S =
        reducer(state as InputState)
}

internal data class OverrideState<S>(internal val newState: S) : ChangedState<S>()

internal object NoStateChange : ChangedState<Nothing>()

/**
 * Transforms the given [state] according to [ChangedState] and returns the new [S].
 */
public fun <S> ChangedState<S>.reduce(state: S): S {
    return when (this) {
        is NoStateChange -> state
        is OverrideState -> newState
        is UnsafeMutateState<*, S> -> this.reduceImpl(state)
    }
}
