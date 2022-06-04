package com.freeletics.flowredux.dsl

/**
 * Represents a state transition.
 * Either use
 *  - [MutateState] to mutate the current state. You get a lambda block with the current state as input and expects
 *  the next state as output.
 * - [OverrideState] to trigger a transition from one state to the next one by simply overriding whatever state was
 * there before. Be careful using this especially if you have handle multiple actions or collect flows within the same
 * state as it really overrides the full state and you may loose information or partial state changes triggered by them.
 * Use [MutateState] in that case.
 * - [NoStateChange] to indicate that this function did not change the state at all. There should be only very little
 * use case for [NoStateChange]. So only use it if you actually know what you do.
 *
 * [ChangeState] doesnt allow you to directly access any property.
 * Then you may wonder how do you write unit test for one of your functions that return a [ChangeState]?
 * You need to call [ChangeState.reduce] to get the actual result of the change state.
 */
public sealed class ChangeState<out S>

/**
 * Sets a new state by directly override any previous state
 */
@Deprecated("call override() on State instead", level = DeprecationLevel.WARNING)
public data class OverrideState<S>(internal val newState: S) : ChangeState<S>()

//TODO rename after removing deprecated OverrideState
internal data class InternalOverrideState<S>(internal val newState: S) : ChangeState<S>()

/**
 * Use this function if you want to "mutate" the current state by copying the old state and modify some properties in
 * the copy of the new state. A common use case is to call .copy() on your state defined as data class.
 */
@Deprecated("call mutate() on State instead", level = DeprecationLevel.WARNING)
public class MutateState<InputState : S, S>(
    internal val reducer: InputState.() -> S
) : ChangeState<S>() {
    @Suppress("UNCHECKED_CAST")
    internal fun reduceImpl(state: S): S =
        reducer(state as InputState)
}

internal class UnsafeMutateState<InputState, S>(
    internal val reducer: InputState.() -> S
) : ChangeState<S>() {
    @Suppress("UNCHECKED_CAST")
    internal fun reduceImpl(state: S): S =
        reducer(state as InputState)
}

/**
 * No change, this is semantically equivalent to use [OverrideState] and pass in the previous state
 */
@Deprecated("call noChange() on State instead", level = DeprecationLevel.WARNING)
public object NoStateChange : ChangeState<Nothing>()

//TODO rename after removing deprecated NoStateChange
internal object InternalNoStateChange : ChangeState<Nothing>()

/**
 * Transforms the given [state] according to [ChangeState] and returns the new [S].
 */
public fun <S> ChangeState<S>.reduce(state: S): S {
    return when (val change = this) {
        is @Suppress("deprecation") OverrideState -> change.newState
        is InternalOverrideState -> change.newState
        is @Suppress("deprecation") NoStateChange -> state
        is InternalNoStateChange -> state
        is @Suppress("deprecation") MutateState<*, S> -> change.reduceImpl(state)
        is UnsafeMutateState<*, S> -> change.reduceImpl(state)
    }
}
