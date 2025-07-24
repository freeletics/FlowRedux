package com.freeletics.flowredux2

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
public fun <S : Parcelable> FlowReduxStateMachineFactory<S, *>.initializeWith(savedStateHandle: SavedStateHandle, initialState: () -> S) {
    stateHolder = ParcelableSavedStateHandleStateHolder(savedStateHandle, initialState)
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
