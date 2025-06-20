package com.freeletics.flowredux2

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Defines which behavior a DSL Block such as `on<Action>` should have whenever
 * a new action or value is emitted.
 */
public sealed interface ExecutionPolicy {
    /**
     * Cancels the previous execution.
     * By applying this [ExecutionPolicy] the previous execution of the
     * same DSL block will be canceled.
     *
     * Example:
     *
     * ```
     * inState<SomeState> {
     *  on<Action1>(executionPolicy = CancelPrevious) { action, stateSnapshot ->
     *          delay(1000)
     *          OverrideState(...)
     *      }
     * }
     * ```
     * Dispatching Action1 two times right after each other means that with this policy the first
     * dispatching of Action1 will be canceled as soon as the second dispatching of Action1
     * is happening. That is what [CancelPrevious] means.
     *
     * (uses flatMapLatest under the hood)
     */
    public object CancelPrevious : ExecutionPolicy

    /**
     * Keeps all previous execution of the DSL block running.
     * Whichever DSL block result is first is the first to execute also `ChangeState`.
     * In other words, the order of execution is not guaranteed at all.
     *
     * Example: Let's assume we have the following DSL definition and dispatch two times
     * very short after each other Action1.
     *
     * ```
     * var invocations = 0
     *
     * inState<SomeState> {
     *  on<Action1>(executionPolicy = Unordered) { action, stateSnapshot ->
     *      invocations++
     *      delay( 1000/invocations )
     *      OverrideState(...)
     *      }
     * }
     * ```
     *
     * By applying [Unordered] policy then there are no guarantees that dispatching two times Action1
     * will result in the same order calling OverrideState.
     * In the example above the delay decreases with each Action1 execution.
     * That means that for example the second dispatching of Action1 actually return OverrideState()
     * before the OverrideState caused by the first dispatching of Action1.
     *
     * With [Unordered] this is explicitly permitted.
     *
     * If you need guarantees that first dispatching of Action1 means also corresponding OverrideState
     * should be executed in the same order as Action being dispatched, then you need [Ordered] as
     * [ExecutionPolicy].
     *
     * (uses flatMapMerge under the hood)
     */
    public object Unordered : ExecutionPolicy

    /**
     * See example of [Unordered].
     *
     * (uses flatMapConcat under the hood)
     */
    public object Ordered : ExecutionPolicy

    /**
     * Limits the values being passed through the handler. The initial value
     * is passed immediately without any delay. After that any value that is
     * received within the given `duration` is dropped. So the handler is
     * called only once per `duration`.
     *
     * Additionally any value received while the handler is still executing
     * is dropped. If `duration` is 500 milliseconds and the handler takes
     * 600 milliseconds then a value received 550 milliseconds after the
     * previous value will still be dropped.
     */
    public class Throttled internal constructor(
        internal val duration: Duration,
        internal val timeSource: TimeSource.WithComparableMarks,
    ) : ExecutionPolicy {
        public constructor(duration: Duration) : this(duration, TimeSource.Monotonic)
    }
}
