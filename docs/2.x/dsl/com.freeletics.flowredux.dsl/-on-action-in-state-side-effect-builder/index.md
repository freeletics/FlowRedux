[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [OnActionInStateSideEffectBuilder](./index.md)

# OnActionInStateSideEffectBuilder

(jvm) `class OnActionInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S> : `[`InStateSideEffectBuilder`](../-in-state-side-effect-builder.md)`<S, A>`

### Constructors

| Name | Summary |
|---|---|
| (jvm) [&lt;init&gt;](-init-.md) | `OnActionInStateSideEffectBuilder(subStateClass: `[`KClass`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)`<SubState>, subActionClass: `[`KClass`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)`<out A>, flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)`, onActionBlock: `[`OnActionBlock`](../-on-action-block.md)`<S, A>)` |
