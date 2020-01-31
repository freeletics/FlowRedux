[flowredux](../index.md) / [com.freeletics.flowredux](index.md) / [Reducer](./-reducer.md)

# Reducer

(jvm) `typealias Reducer<S, A> = (S, A) -> S`

A simple type alias for a reducer function.
A Reducer takes a State and an Action as input and produces a state as output.

If a reducer should not react on a Action, just return the old State.

### Parameters

`S` - The type of the state

`A` - The type of the Actions