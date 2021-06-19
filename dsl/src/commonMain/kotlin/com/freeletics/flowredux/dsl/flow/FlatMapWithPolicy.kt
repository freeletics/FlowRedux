package com.freeletics.flowredux.dsl.flow

import com.freeletics.flowredux.dsl.FlatMapPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge

/**
 * Internal operator to work with [FlatMapPolicy] more fluently
 */
@FlowPreview
@ExperimentalCoroutinesApi
internal fun <T, R> Flow<T>.flatMapWithPolicy(
    flatMapPolicy: FlatMapPolicy,
    transform: suspend (value: T) -> Flow<R>
): Flow<R> =
    when (flatMapPolicy) {
        FlatMapPolicy.LATEST -> this.flatMapLatest(transform)
        FlatMapPolicy.CONCAT -> this.flatMapConcat(transform)
        FlatMapPolicy.MERGE -> this.flatMapMerge(transform = transform)
    }
