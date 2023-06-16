package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.NoStateChange
import com.freeletics.flowredux.dsl.UnsafeMutateState
import com.freeletics.flowredux.dsl.reduce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal abstract class SideEffect<InputState : S, S, A> {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    abstract val isInState: IsInState<S>

    abstract fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>>

    protected inline fun changeState(
        crossinline getState: GetState<S>,
        crossinline block: suspend (InputState) -> ChangedState<S>,
    ): Flow<ChangedState<S>> {
        return flow {
            runOnlyIfInInputState(getState) {
                val changedState = block(it)
                emit(changedState)
            }
        }
    }

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

internal class SideEffectBuilder<InputState : S, S, A>(
    val isInState: IsInState<S>,
    private val builder: () -> SideEffect<InputState, S, A>,
) {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    fun build() = builder()
}

internal typealias GetState<S> = () -> S

internal fun <InputState : S, S, A> SideEffect<InputState, S, A>.produceStateGuarded(
    actions: Flow<Action<A>>,
    getState: GetState<S>,
): Flow<ChangedState<S>> {
    return produceState(actions, getState).map { guardWithIsInState(it, this) }
}

private fun <InputState : S, S> guardWithIsInState(changedState: ChangedState<S>, sideEffect: SideEffect<InputState, S, *>): ChangedState<S> {
    if (changedState is NoStateChange) {
        return changedState
    }
    return UnsafeMutateState<S, S> {
        if (sideEffect.isInState.check(this)) {
            changedState.reduce(this)
        } else {
            this
        }
    }
}
