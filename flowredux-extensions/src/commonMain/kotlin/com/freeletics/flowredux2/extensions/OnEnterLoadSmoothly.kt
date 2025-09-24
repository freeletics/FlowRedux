package com.freeletics.flowredux2.extensions

import com.freeletics.flowredux2.BaseBuilder
import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay

@PublishedApi
internal val defaultLoadingIndicatorDelay: Duration = 500L.milliseconds

@PublishedApi
internal val defaultMinimumLoadingIndicatorDisplayTime: Duration = 500L.milliseconds

/**
 * A custom [BaseBuilder.onEnter] implementation to load data.
 *
 * If the loading [operation] takes less than [loadingIndicatorDelay] then no loading state is shown
 * ([startShowingLoadingIndicator] is not called) and the result is directly delivered.
 *
 * If it takes more than that the loading state is shown ([startShowingLoadingIndicator] is called). It is guaranteed
 * that the loading indicator is shown for at least [minimumLoadingIndicatorDisplayTime] before showing the result.
 *
 * Examples if both durations are 500ms:
 * - `operation` takes 300ms -> no loading indicator shown, result is shown after 300ms
 * - `operation` takes 600ms -> loading indicator shown after 500ms, result is shown after 1s
 * - `operation` takes 1100ms -> loading indicator shown after 500ms, result is shown after 1.1s
 *
 * The initial delay to show the loading indicator will be skipped when [shouldDelayLoadingIndicator] returns `false`.
 * This can be  used to immediately show loading in some scenarios, like the user clicking a button, where
 * delaying the loading indicator would be perceived as the application being unresponsive. In this case it is still
 * guaranteed that but still guarantee that loading is shown for at least [minimumLoadingIndicatorDisplayTime].
 */
@ExperimentalCoroutinesApi
public fun <SubState : S, S : Any> BaseBuilder<SubState, S, *>.onEnterLoadSmoothly(
    startShowingLoadingIndicator: SubState.() -> SubState,
    shouldDelayLoadingIndicator: SubState.() -> Boolean = { true },
    loadingIndicatorDelay: Duration = defaultLoadingIndicatorDelay,
    minimumLoadingIndicatorDisplayTime: Duration = defaultMinimumLoadingIndicatorDisplayTime,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    operation: suspend ChangeableState<SubState>.() -> ChangedState<S>,
) {
    onEnter {
        val timedValue = timeSource.measureTimedValue {
            operation()
        }

        val indicatorStartTime = if (shouldDelayLoadingIndicator(snapshot)) {
            loadingIndicatorDelay
        } else {
            Duration.ZERO
        }
        val indicatorEndTime = indicatorStartTime + minimumLoadingIndicatorDisplayTime

        if (timedValue.duration >= indicatorStartTime && timedValue.duration < indicatorEndTime) {
            delay(indicatorEndTime - timedValue.duration)
        }

        timedValue.value
    }

    onEnter {
        if (shouldDelayLoadingIndicator(snapshot)) {
            delay(loadingIndicatorDelay)
            mutate { startShowingLoadingIndicator() }
        } else {
            noChange()
        }
    }
}
