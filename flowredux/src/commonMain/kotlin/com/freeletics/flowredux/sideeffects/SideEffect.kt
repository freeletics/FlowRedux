package com.freeletics.flowredux.sideeffects

import com.freeletics.flowredux.dsl.ChangedState
import com.freeletics.flowredux.dsl.NoStateChange
import com.freeletics.flowredux.dsl.UnsafeMutateState
import com.freeletics.flowredux.dsl.reduce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal abstract class LegacySideEffect<InputState : S, S, A> : SideEffect<InputState, S, A>() {

    private val actions = Channel<Action<A>>()

    override fun produceState(getState: GetState<S>): Flow<ChangedState<S>> {
        return produceState(actions.receiveAsFlow(), getState)
    }

    abstract fun produceState(actions: Flow<Action<A>>, getState: GetState<S>): Flow<ChangedState<S>>

    override suspend fun sendState(state: S) {
        actions.send(InitialStateAction())
    }

    override suspend fun sendAction(action: A) {
        actions.send(ExternalWrappedAction(action))
    }
}

internal abstract class SideEffect<InputState : S, S, A> {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    abstract val isInState: IsInState<S>

    abstract fun produceState(getState: GetState<S>): Flow<ChangedState<S>>

    open suspend fun sendState(state: S) {}

    open suspend fun sendAction(action: A) {}

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
    private val builder: (InputState) -> SideEffect<InputState, S, A>,
) {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    @Suppress("UNCHECKED_CAST")
    fun build(state: S) = builder(state as InputState)
}

internal class ManagedSideEffect<InputState : S, S, A>(
    private val builder: SideEffectBuilder<InputState, S, A>,
    private val scope: CoroutineScope,
    private val getState: GetState<S>,
    private val stateChanges: SendChannel<ChangedState<S>>,
) {

    private var currentlyActiveSideEffect: CurrentlyActiveSideEffect? = null

    suspend fun sendStateChange(state: S) {
        if (builder.isInState.check(state)) {
            var current = currentlyActiveSideEffect
            if (current == null) {
                val currentSideEffect = builder.build(state)
                val currentJob = scope.launch {
                    currentSideEffect.produceState(getState).collect {
                        // the side effect is in state might be more specific than the one in the builder
                        stateChanges.send(guardWithIsInState(it, currentSideEffect))
                    }
                }
                current = CurrentlyActiveSideEffect(currentJob, currentSideEffect)
                this.currentlyActiveSideEffect = current
            }

            current.sideEffect.sendState(state)
        }
    }

    suspend fun cancelIfNeeded(state: S) {
        val current = currentlyActiveSideEffect
        if (current != null) {
            if (!current.sideEffect.isInState.check(state)) {
                current.job.cancel(StateChangeCancellationException())
                current.job.join()
                this.currentlyActiveSideEffect = null
            }
        }
    }

    suspend fun sendAction(action: A, state: S) {
        val current = currentlyActiveSideEffect
        if (current != null && current.sideEffect.isInState.check(state)) {
            current.sideEffect.sendAction(action)
        }
    }

    private inner class CurrentlyActiveSideEffect(
        val job: Job,
        val sideEffect: SideEffect<InputState, S, A>
    )
}

internal class StateChangeCancellationException : CancellationException("StateMachine moved to a different state")

internal typealias GetState<S> = () -> S

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
