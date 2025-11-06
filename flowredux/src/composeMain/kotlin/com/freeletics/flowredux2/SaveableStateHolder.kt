package com.freeletics.flowredux2

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Sets the initial state for the state machine with a state class that implements [SaveableState]. The implementation can save
 * values of the state into the [SavedStateHandle]. The [SavedStateHandle] is passed to [initialState] on each state machine
 * launch so that previously saved values, if present, can be used to construct the initial state.
 */
public fun <S : SaveableState> FlowReduxStateMachineFactory<S, *>.initializeWith(savedStateHandle: SavedStateHandle, initialState: (SavedStateHandle) -> S) {
    stateHolder = SavedStateHandleStateHolder(savedStateHandle, initialState)
}

/**
 * Sets the initial state for the state machine with a [kotlinx.serialization.Serializable] state class.
 *
 * The first time a state machine is launched from this [FlowReduxStateMachineFactory] instance, [initialState] is called to produce the
 * initial state. Any emitted state from a state machine will be saved to the [savedStateHandle] and subsequent launches will read
 * the state from there instead of calling [initialState].
 */
public inline fun <reified S : Any> FlowReduxStateMachineFactory<S, *>.initializeWith(savedStateHandle: SavedStateHandle, noinline initialState: () -> S) {
    initializeWith(savedStateHandle, serializer<S>(), initialState)
}

@PublishedApi
internal fun <S : Any> FlowReduxStateMachineFactory<S, *>.initializeWith(savedStateHandle: SavedStateHandle, serializer: KSerializer<S>, initialState: () -> S) {
    stateHolder = SerializableSavedStateHandleStateHolder(savedStateHandle, serializer, initialState)
}

public interface SaveableState {
    public fun save(savedStateHandle: SavedStateHandle) {}
}

private class SavedStateHandleStateHolder<S : SaveableState>(
    private val savedStateHandle: SavedStateHandle,
    private val initialState: (SavedStateHandle) -> S,
) : StateHolder<S>() {
    private var state: S? = null

    override fun getState(): S {
        return state ?: initialState(savedStateHandle).also { state = it }
    }

    override fun saveState(s: S) {
        s.save(savedStateHandle)
        state = s
    }
}

private class SerializableSavedStateHandleStateHolder<S : Any>(
    private val savedStateHandle: SavedStateHandle,
    private val serializer: KSerializer<S>,
    private val initialState: () -> S,
) : StateHolder<S>() {
    override fun getState(): S {
        return savedStateHandle.get<SavedState>(STATE)?.let { decodeFromSavedState(serializer, it) } ?: initialState()
    }

    override fun saveState(s: S) {
        savedStateHandle[STATE] = encodeToSavedState(serializer, s)
    }
}

internal const val STATE = "com.freeletics.flowredux2.STATE"
