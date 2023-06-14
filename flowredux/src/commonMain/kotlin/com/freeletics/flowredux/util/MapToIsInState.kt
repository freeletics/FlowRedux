package com.freeletics.flowredux.util

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.sideeffects.Action
import com.freeletics.flowredux.sideeffects.InStateSideEffectBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun <S, A> Flow<Action<S, A>>.mapToIsInState(
    isInState: InStateSideEffectBuilder.IsInState<S>,
    getState: GetState<S>,
): Flow<Boolean> {
    return map { isInState.check(getState()) }
        .distinctUntilChanged()
}
