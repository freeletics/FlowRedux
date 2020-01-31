[flowredux](../index.md) / [com.freeletics.flowredux](index.md) / [SideEffect](./-side-effect.md)

# SideEffect

(jvm) `typealias SideEffect<S, A> = (actions: Flow<A>, state: `[`StateAccessor`](-state-accessor.md)`<S>) -> Flow<A>`

It is a function which takes a stream of actions and returns a stream of actions. Actions in, actions out
(concept borrowed from redux-observable.js.or - so called epics).

### Parameters

`actions` - Input action. Every SideEffect should be responsible to handle a single Action
(i.e using filter or ofType operator)

`state` - [StateAccessor](-state-accessor.md) to get the latest state of the state machine