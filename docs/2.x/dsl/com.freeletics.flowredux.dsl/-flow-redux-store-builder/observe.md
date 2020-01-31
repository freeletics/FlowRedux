[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [FlowReduxStoreBuilder](index.md) / [observe](./observe.md)

# observe

(jvm) `fun <T> observe(flow: Flow<T>, flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)` = FlatMapPolicy.CONCAT, block: `[`StoreWideObserverBlock`](../-store-wide-observer-block.md)`<T, S>): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)

Define some global observer to be able to set the state directly from a flow that you observeWhileInState.
A common use case would be to observeWhileInState a database

