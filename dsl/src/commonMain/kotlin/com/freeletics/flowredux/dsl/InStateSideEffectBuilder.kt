package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.SideEffect

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 */
// TODO verify if this should be internal.
abstract class InStateSideEffectBuilder<InputState : S, S, A> internal constructor() {
    internal abstract fun generateSideEffect(): SideEffect<S, Action<S, A>>


    internal suspend inline fun runOnlyIfInInputState(getState: GetState<S>, isInState: (S) -> Boolean, crossinline block: suspend (InputState) -> Unit) {

        val currentState = getState()
        // only start if is in state condition is still true
        if (isInState(currentState)) {
            try {
                @Suppress("UNCHECKED_CAST")
                val inputState = currentState as InputState
                block(inputState)
            } catch (e: ClassCastException) {
                // it is ok to to swallow the exception as if there is a typecast exception,
                // then a state transition did happen between triggering this side effect (isInState condition)
                // and actually executing this block. This is an expected behavior as it can happen in a
                // concurrent state machine. Therefore just ignoring it is fine.
            }
        }
    }
}
