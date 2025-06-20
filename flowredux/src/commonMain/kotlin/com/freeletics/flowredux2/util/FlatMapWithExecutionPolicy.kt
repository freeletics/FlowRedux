package com.freeletics.flowredux2.util

import com.freeletics.flowredux2.ExecutionPolicy
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.times
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge

/**
 * Internal operator to work with [ExecutionPolicy] more fluently
 */
@ExperimentalCoroutinesApi
internal fun <T, R> Flow<T>.flatMapWithExecutionPolicy(
    executionPolicy: ExecutionPolicy,
    transform: suspend (value: T) -> Flow<R>,
): Flow<R> =
    when (executionPolicy) {
        ExecutionPolicy.CancelPrevious -> this.flatMapLatest(transform)
        ExecutionPolicy.Ordered -> this.flatMapConcat(transform)
        ExecutionPolicy.Unordered -> this.flatMapMerge(transform = transform)
        is ExecutionPolicy.Throttled ->
            this
                .throttleFist(executionPolicy.duration, executionPolicy.timeSource)
                .flatMapConcat(transform = transform)
    }

private fun <T> Flow<T>.throttleFist(windowDuration: Duration, timeSource: TimeSource): Flow<T> = channelFlow {
    var windowStartTime = timeSource.markNow()
    var emitted = false
    collect { value ->
        val delta = windowStartTime.elapsedNow()
        if (delta >= windowDuration) {
            windowStartTime += (delta / windowDuration).toInt() * windowDuration
            emitted = false
        }
        if (!emitted) {
            val result = trySend(value)
            emitted = result.isSuccess
        }
    }
}
    // no buffer so that trySend will not send the value when the handler is still working
    .buffer(0)
