[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [kotlinx.coroutines.flow.Flow](index.md) / [reduxStore](./redux-store.md)

# reduxStore

(jvm) `fun <S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> Flow<A>.reduxStore(logger: FlowReduxLogger? = null, initialStateSupplier: () -> S, block: `[`FlowReduxStoreBuilder`](../-flow-redux-store-builder/index.md)`<S, A>.() -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): Flow<S>`
`fun <S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> Flow<A>.reduxStore(logger: FlowReduxLogger? = null, initialState: S, block: `[`FlowReduxStoreBuilder`](../-flow-redux-store-builder/index.md)`<S, A>.() -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): Flow<S>`

Provides a fluent DSL to specify a ReduxStore

