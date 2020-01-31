[flowredux](../index.md) / [com.freeletics.flowredux](./index.md)

## Package com.freeletics.flowredux

### Types

| Name | Summary |
|---|---|
| (jvm) [FlowReduxLogger](-flow-redux-logger/index.md) | `interface FlowReduxLogger` |
| (jvm) [Reducer](-reducer.md) | A simple type alias for a reducer function. A Reducer takes a State and an Action as input and produces a state as output.`typealias Reducer<S, A> = (S, A) -> S` |
| (jvm) [SideEffect](-side-effect.md) | It is a function which takes a stream of actions and returns a stream of actions. Actions in, actions out (concept borrowed from redux-observable.js.or - so called epics).`typealias SideEffect<S, A> = (actions: Flow<A>, state: `[`StateAccessor`](-state-accessor.md)`<S>) -> Flow<A>` |
| (jvm) [StateAccessor](-state-accessor.md) | The StateAccessor is basically just a deferred way to get a state of a [reduxStore](kotlinx.coroutines.flow.-flow/redux-store.md) at any given point in time. So you have to call this method to get the state.`typealias StateAccessor<S> = () -> S` |

### Exceptions

| Name | Summary |
|---|---|
| (jvm) [ReducerException](-reducer-exception/index.md) | `class ReducerException : `[`RuntimeException`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-runtime-exception/index.html) |

### Extensions for External Classes

| Name | Summary |
|---|---|
| (jvm) [kotlinx.coroutines.flow.Flow](kotlinx.coroutines.flow.-flow/index.md) |  |
