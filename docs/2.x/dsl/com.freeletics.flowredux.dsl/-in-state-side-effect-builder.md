[dsl](../index.md) / [com.freeletics.flowredux.dsl](index.md) / [InStateSideEffectBuilder](./-in-state-side-effect-builder.md)

# InStateSideEffectBuilder

(jvm) `abstract class InStateSideEffectBuilder<S, A>`

It's just not an Interface to not expose internal class `Action` to the public.
Thus it's an internal abstract class but you can think of it as an internal interface.

### Inheritors

| Name | Summary |
|---|---|
| (jvm) [OnActionInStateSideEffectBuilder](-on-action-in-state-side-effect-builder/index.md) | `class OnActionInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S> : `[`InStateSideEffectBuilder`](./-in-state-side-effect-builder.md)`<S, A>` |
| (jvm) [OnEnterInStateSideEffectBuilder](-on-enter-in-state-side-effect-builder/index.md) | A builder that generates a [SideEffect](#) that triggers every time the state machine enters a certain state.`class OnEnterInStateSideEffectBuilder<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`InStateSideEffectBuilder`](./-in-state-side-effect-builder.md)`<S, A>` |
