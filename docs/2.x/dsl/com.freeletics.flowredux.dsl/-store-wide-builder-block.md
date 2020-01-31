[dsl](../index.md) / [com.freeletics.flowredux.dsl](index.md) / [StoreWideBuilderBlock](./-store-wide-builder-block.md)

# StoreWideBuilderBlock

(jvm) `abstract class StoreWideBuilderBlock<S, A>`

It's just not an Interface to not expose internal class `Action` to the public.
Thus it's an internal abstract class but you can think of it as an internal interface.

It's also not a sealed class because no need for it (no need to enumerate subclasses as
we only care about the abstract functions this class exposes).
Also sealed class would mean to move all subclasses into the same File.
That is not that nice as it all subclasses are implementation detail heavy.
There is no need to have a hundreds of lines of code in one file just to have sealed classes.

### Inheritors

| Name | Summary |
|---|---|
| (jvm) [InStateBuilderBlock](-in-state-builder-block/index.md) | `class InStateBuilderBlock<S : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`, SubState : S, A : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`> : `[`StoreWideBuilderBlock`](./-store-wide-builder-block.md)`<S, A>` |
