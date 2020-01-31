[flowredux](../index.md) / [com.freeletics.flowredux](index.md) / [StateAccessor](./-state-accessor.md)

# StateAccessor

(jvm) `typealias StateAccessor<S> = () -> S`

The StateAccessor is basically just a deferred way to get a state of a [reduxStore](kotlinx.coroutines.flow.-flow/redux-store.md) at any given point in time.
So you have to call this method to get the state.

