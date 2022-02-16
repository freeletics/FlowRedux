package com.freeletics.flowredux.dsl

/**
 * Defines which behavior a DSL Block such as `on<Action>` should have whenever
 * a new action or value is emitted.
 */
public enum class ExecutionPolicy {
    /**
     * Cancels the previous execution.
     * By applying this [ExecutionPolicy] the previous execution of the
     * same DSL block will be canceled.
     *
     * Example:
     *
     * ```
     * inState<SomeState> {
     *  on<Action1>(executionPolicy = CANCEL_PREVIOUS_EXECUTION) { action, stateSnapshot ->
     *          delay(1000)
     *          OverrideState(...)
     *      }
     * }
     * ```
     * Dispatching Action1 two times right after each other means that with this policy the first
     * dispatching of Action1 will be canceled as soon as the second dispatching of Action1
     * is happening. That is what [CANCEL_PREVIOUS] means.
     *
     * (uses flatMapLatest under the hood)
     */
    CANCEL_PREVIOUS,

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
     *  on<Action1>(executionPolicy = UNORDERED) { action, stateSnapshot ->
     *      invocations++
     *      delay( 1000/invocations )
     *      OverrideState(...)
     *      }
     * }
     * ```
     *
     * By applying [UNORDERED] policy then there are no guarantees that dispatching two times Action1
     * will result in the same order calling OverrideState.
     * In the example above the delay decreases with each Action1 execution.
     * That means that for example the second dispatching of Action1 actually return OverrideState()
     * before the OverrideState caused by the first dispatching of Action1.
     *
     * With [UNORDERED] this is explicitly permitted.
     *
     * If you need guarantees that first dispatching of Action1 means also corresponding OverrideState
     * should be executed in the same order as Action being dispatched, then you need [ORDERED] as
     * [ExecutionPolicy].
     *
     * (uses flatMapMerge under the hood)
     */
    UNORDERED,

    /**
     * See example of [UNORDERED].
     *
     * (uses flatMapConcat under the hood)
     */
    ORDERED
}
