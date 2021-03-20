package com.freeletics.flowredux.dsl

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
