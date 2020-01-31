[dsl](../index.md) / [com.freeletics.flowredux.dsl](./index.md)

## Package com.freeletics.flowredux.dsl

### Types

| Name | Summary |
|---|---|
| (jvm) [FlatMapPolicy](-flat-map-policy/index.md) | Defines which flatMap behavior should be applied whenever a new values is emitted`enum class FlatMapPolicy` |
| (jvm) [FlowReduxStateMachine](-flow-redux-state-machine/index.md) | `abstract class FlowReduxStateMachine<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>` |
| (jvm) [FlowReduxStoreBuilder](-flow-redux-store-builder/index.md) | `class FlowReduxStoreBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>` |
| (jvm) [InStateBuilderBlock](-in-state-builder-block/index.md) | `class InStateBuilderBlock<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`StoreWideBuilderBlock`](-store-wide-builder-block.md)`<S, A>` |
| (jvm) [InStateObserverBlock](-in-state-observer-block.md) | `typealias InStateObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: `[`SetState`](-set-state.md)`<S>) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [InStateOnEnterBlock](-in-state-on-enter-block.md) | `typealias InStateOnEnterBlock<S> = suspend (getState: StateAccessor<S>, setState: `[`SetState`](-set-state.md)`<S>) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [InStateSideEffectBuilder](-in-state-side-effect-builder.md) | It's just not an Interface to not expose internal class `Action` to the public. Thus it's an internal abstract class but you can think of it as an internal interface.`abstract class InStateSideEffectBuilder<S, A>` |
| (jvm) [OnActionBlock](-on-action-block.md) | `typealias OnActionBlock<S, A> = suspend (action: A, getState: StateAccessor<S>, setState: `[`SetState`](-set-state.md)`<S>) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [OnActionInStateSideEffectBuilder](-on-action-in-state-side-effect-builder/index.md) | `class OnActionInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S> : `[`InStateSideEffectBuilder`](-in-state-side-effect-builder.md)`<S, A>` |
| (jvm) [OnEnterInStateSideEffectBuilder](-on-enter-in-state-side-effect-builder/index.md) | A builder that generates a [SideEffect](#) that triggers every time the state machine enters a certain state.`class OnEnterInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`InStateSideEffectBuilder`](-in-state-side-effect-builder.md)`<S, A>` |
| (jvm) [SetState](-set-state.md) | A simple type alias for a function that we call "SetState". What it does is it takes a current state and returns the next state.`typealias SetState<S> = suspend ((currentState: S) -> S) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [StoreWideBuilderBlock](-store-wide-builder-block.md) | It's just not an Interface to not expose internal class `Action` to the public. Thus it's an internal abstract class but you can think of it as an internal interface.`abstract class StoreWideBuilderBlock<S, A>` |
| (jvm) [StoreWideObserverBlock](-store-wide-observer-block.md) | `typealias StoreWideObserverBlock<T, S> = suspend (value: T, getState: StateAccessor<S>, setState: `[`SetState`](-set-state.md)`<S>) -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |

### Extensions for External Classes

| Name | Summary |
|---|---|
| (jvm) [kotlinx.coroutines.flow.Flow](kotlinx.coroutines.flow.-flow/index.md) |  |
