package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.NoStateChange
import com.freeletics.flowredux2.NoStateChangeSkipEmission
import com.freeletics.flowredux2.TaggedLogger
import com.freeletics.flowredux2.UnsafeMutateState
import com.freeletics.flowredux2.logD
import com.freeletics.flowredux2.logV
import com.freeletics.flowredux2.reduce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal abstract class SideEffect<InputState : S, S, A> {
    fun interface IsInState<S> {
        fun check(state: S): Boolean
    }

    abstract val isInState: IsInState<S>

    abstract val logger: TaggedLogger?

    abstract fun produceState(getState: GetState<S>): Flow<ChangedState<S>>

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
            } catch (_: ClassCastException) {
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

internal abstract class ActionBasedSideEffect<InputState : S, S, A> : SideEffect<InputState, S, A>() {
    private val actionChannel = Channel<A>()
    protected val actions get() = actionChannel.receiveAsFlow()

    override suspend fun sendAction(action: A) {
        actionChannel.send(action)
    }
}

internal class SideEffectBuilder<InputState : S, S, A>(
    val isInState: IsInState<S>,
    val logger: TaggedLogger?,
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

    fun startIfNeeded(state: S) {
        builder.logger.logV { "Checking if side effect needs to be started for state $state" }
        if (builder.isInState.check(state)) {
            var current = currentlyActiveSideEffect
            if (current == null) {
                builder.logger.logD { "Side effect start $state" }
                val currentSideEffect = builder.build(state)
                val currentJob = scope.launch {
                    currentSideEffect.produceState(getState).collect {
                        // the side effect is in state might be more specific than the one in the builder
                        stateChanges.send(guardWithIsInState(it, currentSideEffect, builder.logger))
                    }
                }
                current = CurrentlyActiveSideEffect(currentJob, currentSideEffect)
                this.currentlyActiveSideEffect = current
            } else {
                builder.logger.logV { "Side effect already active" }
            }
        } else {
            builder.logger.logV { "Side effect should not be active" }
        }
    }

    suspend fun cancelIfNeeded(state: S) {
        val current = currentlyActiveSideEffect
        if (current != null) {
            builder.logger.logV { "Checking if side effect needs to be cancelled for state $state" }
            if (!current.sideEffect.isInState.check(state)) {
                builder.logger.logD { "Side effect cancellation $state" }
                current.job.cancel(StateChangeCancellationException())
                current.job.join()
                this.currentlyActiveSideEffect = null
            } else {
                builder.logger.logV { "Side effect should continue running" }
            }
        }
    }

    suspend fun sendAction(action: A, state: S) {
        val current = currentlyActiveSideEffect
        if (current != null && current.sideEffect.isInState.check(state)) {
            builder.logger.logV { "Sending $action" }
            current.sideEffect.sendAction(action)
        }
    }

    private inner class CurrentlyActiveSideEffect(
        val job: Job,
        val sideEffect: SideEffect<InputState, S, A>,
    )
}

internal class StateChangeCancellationException : CancellationException("StateMachine moved to a different state")

internal typealias GetState<S> = () -> S

private fun <InputState : S, S> guardWithIsInState(changedState: ChangedState<S>, sideEffect: SideEffect<InputState, S, *>, logger: TaggedLogger?): ChangedState<S> {
    if (changedState is NoStateChange || changedState is NoStateChangeSkipEmission) {
        return changedState
    }
    return UnsafeMutateState<S, S> {
        if (sideEffect.isInState.check(this)) {
            changedState.reduce(this).also {
                logger.logV { "ChangedState $this -> $it" }
            }
        } else {
            logger.logV { "ChangedState skipped because of state change" }
            this
        }
    }
}
