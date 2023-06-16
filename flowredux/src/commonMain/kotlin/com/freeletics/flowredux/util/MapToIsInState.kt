package com.freeletics.flowredux.util

import com.freeletics.flowredux.sideeffects.Action
import com.freeletics.flowredux.sideeffects.GetState
import com.freeletics.flowredux.sideeffects.SideEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun <S, A> Flow<Action<S, A>>.mapToIsInState(
    isInState: SideEffect.IsInState<S>,
    getState: GetState<S>,
): Flow<Boolean> {
    return map { isInState.check(getState()) }
        .distinctUntilChanged()
}
