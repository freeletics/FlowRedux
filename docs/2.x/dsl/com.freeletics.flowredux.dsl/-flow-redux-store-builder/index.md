[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [FlowReduxStoreBuilder](./index.md)

# FlowReduxStoreBuilder

(jvm) `class FlowReduxStoreBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>`

### Constructors

| Name | Summary |
|---|---|
| (jvm) [&lt;init&gt;](-init-.md) | `FlowReduxStoreBuilder()` |

### Properties

| Name | Summary |
|---|---|
| (jvm) [builderBlocks](builder-blocks.md) | `val builderBlocks: `[`ArrayList`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-array-list/index.html)`<`[`StoreWideBuilderBlock`](../-store-wide-builder-block.md)`<S, A>>` |

### Functions

| Name | Summary |
|---|---|
| (jvm) [inState](in-state.md) | Define what happens if the store is in a certain state.`fun <SubState : S> inState(block: `[`InStateBuilderBlock`](../-in-state-builder-block/index.md)`<S, SubState, A>.() -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [observe](observe.md) | Define some global observer to be able to set the state directly from a flow that you observeWhileInState. A common use case would be to observeWhileInState a database`fun <T> observe(flow: Flow<T>, flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)` = FlatMapPolicy.CONCAT, block: `[`StoreWideObserverBlock`](../-store-wide-observer-block.md)`<T, S>): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
