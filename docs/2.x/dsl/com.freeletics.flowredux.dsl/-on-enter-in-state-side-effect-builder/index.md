[dsl](../../index.md) / [com.freeletics.flowredux.dsl](../index.md) / [OnEnterInStateSideEffectBuilder](./index.md)

# OnEnterInStateSideEffectBuilder

(jvm) `class OnEnterInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`InStateSideEffectBuilder`](../-in-state-side-effect-builder.md)`<S, A>`

A builder that generates a [SideEffect](#) that triggers every time the state machine enters
a certain state.

### Constructors

| Name | Summary |
|---|---|
| (jvm) [&lt;init&gt;](-init-.md) | A builder that generates a [SideEffect](#) that triggers every time the state machine enters a certain state.`OnEnterInStateSideEffectBuilder(subStateClass: `[`KClass`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)`<out S>, flatMapPolicy: `[`FlatMapPolicy`](../-flat-map-policy/index.md)`, block: `[`InStateOnEnterBlock`](../-in-state-on-enter-block.md)`<S>)` |
