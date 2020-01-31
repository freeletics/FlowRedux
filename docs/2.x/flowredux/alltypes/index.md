

### All Types

| Name | Summary |
|---|---|
|(jvm)  (extensions in package com.freeletics.flowredux)

##### [kotlinx.coroutines.flow.Flow](../com.freeletics.flowredux/kotlinx.coroutines.flow.-flow/index.md)


|(jvm)

##### [com.freeletics.flowredux.FlowReduxLogger](../com.freeletics.flowredux/-flow-redux-logger/index.md)


|(jvm)

##### [com.freeletics.flowredux.Reducer](../com.freeletics.flowredux/-reducer.md)

A simple type alias for a reducer function.
A Reducer takes a State and an Action as input and produces a state as output.


|(jvm)

##### [com.freeletics.flowredux.ReducerException](../com.freeletics.flowredux/-reducer-exception/index.md)


|(jvm)

##### [com.freeletics.flowredux.SideEffect](../com.freeletics.flowredux/-side-effect.md)

It is a function which takes a stream of actions and returns a stream of actions. Actions in, actions out
(concept borrowed from redux-observable.js.or - so called epics).


|(jvm)

##### [com.freeletics.flowredux.StateAccessor](../com.freeletics.flowredux/-state-accessor.md)

The StateAccessor is basically just a deferred way to get a state of a [reduxStore](../com.freeletics.flowredux/kotlinx.coroutines.flow.-flow/redux-store.md) at any given point in time.
So you have to call this method to get the state.


