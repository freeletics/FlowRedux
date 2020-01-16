package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 */
// TODO verify if this should be internal.
abstract class InStateSideEffectBuilder<S, A> internal constructor() {
    internal abstract fun generateSideEffect(): SideEffect<S, Action<S, A>>
}