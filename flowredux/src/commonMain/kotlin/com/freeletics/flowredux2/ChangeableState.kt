package com.freeletics.flowredux2

/**
 * Allows access to a [snapshot] that can be used to access the current state at the time the action
 * or event happened.
 */
public sealed class State<InputState : Any>(
    public val snapshot: InputState,
)

/**
 * Allows to create [ChangedState] objects to change the state as a result of DSL
 * methods like [InStateBuilder.on] or  [InStateBuilder.onEnter].
 *
 * Note that [snapshot] might be outdated by the time the code runs and should
 * never be used within [mutate] or to create an object that is passed to [override]
 */
public class ChangeableState<InputState : Any>(
    snapshot: InputState,
) : State<InputState>(snapshot) {
    /**
     * Use this function if you want to "mutate" the current state by copying the old state
     * and modify some properties in the copy of the new state. A common use case is to call
     * .copy() on your state defined as data class.
     *
     * [snapshot] should never be accessed within [reducer]. Use the `this` of the lambda which
     * is guaranteed to be the current state at the time of execution instead.
     */
    public fun mutate(reducer: InputState.() -> InputState): ChangedState<InputState> {
        return UnsafeMutateState(reducer)
    }

    /**
     * Use this function if you want to override the previous state with another state based on
     * the current state.
     *
     * [snapshot] should never be accessed within [reducer]. Use the `this` of the lambda which
     * is guaranteed to be the current state at the time of execution instead.
     */
    public fun <S : Any> override(reducer: InputState.() -> S): ChangedState<S> {
        return UnsafeMutateState(reducer)
    }

    /**
     * No change, this is semantically equivalent to use [override] or [mutate] and return `this`.
     */
    public fun <S : Any> noChange(): ChangedState<S> {
        return NoStateChange
    }
}

/**
 * Represents a state transition. Instances of this a created through the [ChangeableState.mutate],
 * [ChangeableState.override] and [ChangeableState.noChange] methods.
 *
 * [ChangedState] does not allow you to directly access any property.
 * Then you may wonder how do you write unit test for one of your functions that return a [ChangedState]?
 * You need to call [ChangedState.reduce] to get the actual result of the change state.
 */
public sealed class ChangedState<out S>

internal class UnsafeMutateState<InputState, S>(
    internal val reducer: InputState.() -> S,
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
