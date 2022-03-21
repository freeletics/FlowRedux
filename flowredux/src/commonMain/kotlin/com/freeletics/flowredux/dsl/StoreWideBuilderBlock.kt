package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 *
 * It's also not a sealed class because no need for it (no need to enumerate subclasses as
 * we only care about the abstract functions this class exposes).
 * Also sealed class would mean to move all subclasses into the same File.
 * That is not that nice as it all subclasses are implementation detail heavy.
 * There is no need to have a hundreds of lines of code in one file just to have sealed classes.
 */
public abstract class StoreWideBuilderBlock<S, A> internal constructor() {

    /**
     * You can think of this as some kind of factory method that generates a list of sideeffects
     */
    internal abstract fun generateSideEffects(): List<SideEffect<S, Action<S, A>>>
}
