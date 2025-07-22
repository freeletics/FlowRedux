package com.freeletics.flowredux2

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle

public fun <S : Parcelable> parcelableStateHolder(savedStateHandle: SavedStateHandle, initialState: () -> S): StateHolder<S> {
    return ParcelableSavedStateHandleStateHolder(savedStateHandle, initialState)
}

private class ParcelableSavedStateHandleStateHolder<S : Parcelable>(
    private val savedStateHandle: SavedStateHandle,
    private val initialState: () -> S,
) : StateHolder<S>() {
    override fun getState(): S {
        return savedStateHandle[STATE] ?: initialState()
    }

    override fun saveState(s: S) {
        savedStateHandle[STATE] = s
    }
}
