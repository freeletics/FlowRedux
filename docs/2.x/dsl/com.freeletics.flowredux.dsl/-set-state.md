[dsl](../index.md) / [com.freeletics.flowredux.dsl](index.md) / [SetState](./-set-state.md)

# SetState

(jvm) `typealias SetState<S> = suspend ((currentState: S) -> S) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)

A simple type alias for a function that we call "SetState".
What it does is it takes a current state and returns the next state.

