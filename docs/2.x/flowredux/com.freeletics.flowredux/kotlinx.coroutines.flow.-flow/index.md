[flowredux](../../index.md) / [com.freeletics.flowredux](../index.md) / [kotlinx.coroutines.flow.Flow](./index.md)

### Extensions for kotlinx.coroutines.flow.Flow

| Name | Summary |
|---|---|
| (jvm) [reduxStore](redux-store.md) | `fun <A, S> Flow<A>.reduxStore(initialStateSupplier: () -> S, sideEffects: `[`Iterable`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-iterable/index.html)`<`[`SideEffect`](../-side-effect.md)`<S, A>>, logger: `[`FlowReduxLogger`](../-flow-redux-logger/index.md)`? = null, reducer: `[`Reducer`](../-reducer.md)`<S, A>): Flow<S>` |
