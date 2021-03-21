package com.freeletics.flowredux.dsl

/**
 * Represents a state transition.
 * Either use
 * - [SetState] to trigger a transition from one state to the next one
 * - [MutateState] to mutate the current state
 * - [NoStateChange] to indicate that this function did not change the state at all. There should be only very little
 * use case for [NoStateChange]. So only use it if you actually know what you do.
 *
 * [ChangeState] doesnt allow you to directly access any property.
 * Then you may wonder how do you write unit test for one of your functions that return a [ChangeState]?
 * You need to call [ChangeState.reduce] to get the actual result of the change state.
 */
sealed class ChangeState<out S>

/**
 * Sets a new state by directly override any previous state
 */
data class SetState<S>(internal val newState: S) : ChangeState<S>()

/**
 * Use this function if you want to "mutate" the current state by copying the old state and modify some properties in
 * the copy of the new state. A common use case is to call .copy() on your state defined as data class.
 */
class MutateState<InputState : S, S>(internal val reducer: InputState.() -> S) : ChangeState<S>(){
    @Suppress("UNCHECKED_CAST")
    internal fun reduceImpl(state : S) : S =
        reducer(state as InputState)
}


/**
 * No change, this is semantially equivalent to use [SetState] and pass in the previous state
 */
object NoStateChange : ChangeState<Nothing>()

fun <S> ChangeState<S>.reduce(state: S): S {
    return when (val change = this) {
        is SetState -> change.newState
        is NoStateChange -> state // TODO throw exception instead?
        is MutateState<*, S> -> change.reduceImpl(state)
    }
}
