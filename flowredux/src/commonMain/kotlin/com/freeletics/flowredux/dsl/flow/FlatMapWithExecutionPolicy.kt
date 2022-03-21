package com.freeletics.flowredux.dsl.flow

import com.freeletics.flowredux.dsl.ExecutionPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge

/**
 * Internal operator to work with [ExecutionPolicy] more fluently
 */
@FlowPreview
@ExperimentalCoroutinesApi
internal fun <T, R> Flow<T>.flatMapWithExecutionPolicy(
    executionPolicy: ExecutionPolicy,
    transform: suspend (value: T) -> Flow<R>
): Flow<R> =
    when (executionPolicy) {
        ExecutionPolicy.CANCEL_PREVIOUS -> this.flatMapLatest(transform)
        ExecutionPolicy.ORDERED -> this.flatMapConcat(transform)
        ExecutionPolicy.UNORDERED -> this.flatMapMerge(transform = transform)
    }
