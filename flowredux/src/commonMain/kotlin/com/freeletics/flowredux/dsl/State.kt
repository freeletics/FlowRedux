package com.freeletics.flowredux.dsl

/**
 * Allows to create [ChangedState] objects to change the state as a result of DSL
 * methods like [InStateBuilderBlock.on] or  [InStateBuilderBlock.onEnter].
 *
 * The [snapshot] property can be used to access the current state at the time the action
 * or event happened. Note that it might be outdated by the time the code runs and should
 * never be used within [mutate] or to create an object that is passed to [override]
 */
public class State<InputState : Any>(
    public val snapshot: InputState
) {
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
