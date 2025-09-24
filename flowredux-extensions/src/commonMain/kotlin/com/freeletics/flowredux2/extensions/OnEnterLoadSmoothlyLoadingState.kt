// Use InlineOnly like the stdlib does to allow having 2 type constraints on SubState
// where one of the 2 is another type parameter. The compiler usually doesn't allow this
// and has the BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER check for it but lifts
// the restriction for methods annotated with @InlineOnly which unfortunately is internal.
@file:Suppress("INVISIBLE_REFERENCE")

package com.freeletics.flowredux2.extensions

import com.freeletics.flowredux2.BaseBuilder
import com.freeletics.flowredux2.ChangeableState
import com.freeletics.flowredux2.ChangedState
import kotlin.internal.InlineOnly
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * To be used for states that use [onEnterLoadSmoothly] in a state machine.
 */
public interface LoadingState<T : LoadingState<T>> {
    /**
     * When `true` the UI should show a loading indicator. Otherwise it should just
     * stay blank.
     */
    public val showLoadingIndicator: Boolean

    /**
     * Returns a new instance of [T] where [showLoadingIndicator] is `true`.
     */
    public fun withShowLoadingIndicatorEnabled(): T
}

/**
 * A version of [onEnterLoadSmoothly] that simplifies the usage by using the [LoadingState] interface. This way
 * the state in which [onEnterLoadSmoothly] is used in just needs to implement the interface and the usage of
 * the DSL method is simplified to only requiring to pass [operation] making it look closer to the regular
 * `onEnter` and other DSL methods.
 */
@InlineOnly
@ExperimentalCoroutinesApi
public inline fun <SubState, S : Any> BaseBuilder<SubState, S, *>.onEnterLoadSmoothly(
    loadingIndicatorDelay: Duration = defaultLoadingIndicatorDelay,
    minimumLoadingIndicatorDisplayTime: Duration = defaultMinimumLoadingIndicatorDisplayTime,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    noinline operation: suspend ChangeableState<SubState>.() -> ChangedState<S>,
) where SubState : S, SubState : LoadingState<SubState> {
    onEnterLoadSmoothly(
        startShowingLoadingIndicator = { withShowLoadingIndicatorEnabled() },
        shouldDelayLoadingIndicator = { !showLoadingIndicator },
        loadingIndicatorDelay = loadingIndicatorDelay,
        minimumLoadingIndicatorDisplayTime = minimumLoadingIndicatorDisplayTime,
        timeSource = timeSource,
        operation = operation,
    )
}
