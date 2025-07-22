package com.freeletics.flowredux2

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

public fun <S : SaveableState> savedStateHandleStateHolder(savedStateHandle: SavedStateHandle, initialState: (SavedStateHandle) -> S): StateHolder<S> {
    return SavedStateHandleStateHolder(savedStateHandle, initialState)
}

public inline fun <reified S : Any> serializableStateHolder(savedStateHandle: SavedStateHandle, noinline initialState: () -> S): StateHolder<S> {
    return serializableStateHolder(savedStateHandle, serializer<S>(), initialState)
}

@PublishedApi
internal fun <S : Any> serializableStateHolder(savedStateHandle: SavedStateHandle, serializer: KSerializer<S>, initialState: () -> S): StateHolder<S> {
    return SerializableSavedStateHandleStateHolder(savedStateHandle, serializer, initialState)
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
