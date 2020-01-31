[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [InStateBuilderBlock](./index.md)

# InStateBuilderBlock

(jvm) `class InStateBuilderBlock<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`StoreWideBuilderBlock`](../-store-wide-builder-block.md)`<S, A>`

### Constructors

| Name | Summary |
|---|---|
| (jvm) [&lt;init&gt;](-init-.md) | `InStateBuilderBlock(_subStateClass: `[`KClass`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)`<SubState>)` |

### Properties

| Name | Summary |
|---|---|
| (jvm) [_inStateSideEffectBuilders](_in-state-side-effect-builders.md) | `val _inStateSideEffectBuilders: `[`ArrayList`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-array-list/index.html)`<`[`InStateSideEffectBuilder`](../-in-state-side-effect-builder.md)`<S, A>>` |
| (jvm) [_subStateClass](_sub-state-class.md) | `val _subStateClass: `[`KClass`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)`<SubState>` |

### Functions

| Name | Summary |
|---|---|
| (jvm) [observeWhileInState](observe-while-in-state.md) | `fun <T> observeWhileInState(flow: Flow<T>, flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)` = FlatMapPolicy.CONCAT, block: `[`InStateObserverBlock`](../-in-state-observer-block.md)`<T, S>): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [on](on.md) | `fun <SubAction : A> on(flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)` = FlatMapPolicy.LATEST, block: `[`OnActionBlock`](../-on-action-block.md)`<S, SubAction>): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| (jvm) [onEnter](on-enter.md) | Triggers every time the state machine enters this state.`fun onEnter(flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)` = FlatMapPolicy.LATEST, block: `[`InStateOnEnterBlock`](../-in-state-on-enter-block.md)`<S>): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
