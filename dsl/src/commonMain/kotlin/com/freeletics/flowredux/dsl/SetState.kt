package com.freeletics.flowredux.dsl

/**
 * A simple type alias for a function that we call "SetState".
 * What it does is it takes a current state and returns the next state.
 */
typealias SetState<S> =suspend ((currentState: S) -> S) -> Unit

// class SetStateImpl<S>(runOnlyIf: (S) -> Boolean = { true }, reduceBlock: suspend ((currentState: S) -> S) -> Unit)