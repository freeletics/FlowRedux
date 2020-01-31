[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [FlowReduxStateMachine](./index.md)

# FlowReduxStateMachine

(jvm) `abstract class FlowReduxStateMachine<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>`

### Constructors

| Name | Summary |
|---|---|
| (jvm) [&lt;init&gt;](-init-.md) | `FlowReduxStateMachine(initialStateSupplier: () -> S)`<br>`FlowReduxStateMachine(initialState: S)`<br>`FlowReduxStateMachine(logger: FlowReduxLogger?, initialState: S)`<br>`FlowReduxStateMachine(logger: FlowReduxLogger?, initialStateSupplier: () -> S)` |

### Properties

| Name | Summary |
|---|---|
| (jvm) [state](state.md) | `val state: Flow<S>` |

### Functions

| Name | Summary |
|---|---|
| (jvm) [dispatch](dispatch.md) | `suspend fun dispatch(action: A): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [spec](spec.md) | `fun spec(specBlock: `[`FlowReduxStoreBuilder`](../-flow-redux-store-builder/index.md)`<S, A>.() -> `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
