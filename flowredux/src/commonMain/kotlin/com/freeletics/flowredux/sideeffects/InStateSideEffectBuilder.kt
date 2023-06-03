package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.SideEffect

/**
 * It's just not an Interface to not expose internal class `Action` to the public.
 * Thus it's an internal abstract class but you can think of it as an internal interface.
 */
internal abstract class InStateSideEffectBuilder<InputState : S, S, A> {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    abstract val isInState: IsInState<S>

    abstract fun generateSideEffect(): SideEffect<S, A>

    protected suspend inline fun runOnlyIfInInputState(
        getState: GetState<S>,
        crossinline block: suspend (InputState) -> Unit,
    ) {
        val currentState = getState()
        // only start if is in state condition is still true
        if (isInState.check(currentState)) {
            val inputState = try {
                @Suppress("UNCHECKED_CAST")
                currentState as InputState
            } catch (e: ClassCastException) {
                // it is ok to to swallow the exception as if there is a typecast exception,
                // then a state transition did happen between triggering this side effect (isInState condition)
                // and actually executing this block. This is an expected behavior as it can happen in a
                // concurrent state machine. Therefore just ignoring it is fine.
                return
            }

            block(inputState)
        }
    }
}
