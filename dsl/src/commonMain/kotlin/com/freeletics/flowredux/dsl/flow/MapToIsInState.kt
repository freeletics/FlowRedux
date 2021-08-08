package com.freeletics.flowredux.dsl.flow

import com.freeletics.flowredux.GetState
import com.freeletics.flowredux.dsl.internal.Action
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun <S, A> Flow<Action<S, A>>.mapToIsInState(
    isInState: (S) -> Boolean,
    getState: GetState<S>,
) : Flow<Boolean> {
    return map { isInState(getState()) }
        .distinctUntilChanged()
}
