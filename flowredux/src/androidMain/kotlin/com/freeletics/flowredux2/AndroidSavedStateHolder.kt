package com.freeletics.flowredux2

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle

/**
 * Sets the initial state for the state machine with a [Parcelable] state class.
 *
 * The first time a state machine is launched from this [FlowReduxStateMachineFactory] instance, [initialState] is called to produce the
 * initial state. Any emitted state from a state machine will be saved to the [savedStateHandle] and subsequent launches will read
 * the state from there instead of calling [initialState].
 */
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
