package com.freeletics.flowredux.dsl

/**
 * [SetState] allows you to set the state of your state machine. It's just a convinient
 * way along with DSL.
 * Usage:
 *
 * ```kotlin
 * fun doSomething(setState : SetState<S>) {
 *   setState { SomeState } // Return the new state in the lambda.
 * }
 * ```
 *
 * You can also specify an parameter `runIf` that is checked before actually running
 * setState block:
 *
 * ```kotlin
 * fun doSomething(setState : SetState<S>) {
 *   // Only executes { SomeState } if runIf={...} returns true
 *   setState(runIf={state -> state is FooState}) { SomeState }
 * }
 * ```
 */
// TODO Is abstract really needed? Could be just class and remove SetStateImpl?
//  it looks like we need abstract to have a unit test friendly fake implementation of
//  SetState
abstract class SetState<S>(
    private val defaultRunIf: (S) -> Boolean
) {
    abstract suspend operator fun invoke(
        /**
         * If this lambda return true only then reduce block will be executed
         */
        runIf: (S) -> Boolean = defaultRunIf,
        /**
         * This lambda gets the current state of the state machine as input and returns
         * the new state the state machine should transition to as return value.
         */
        reduce: (currentState: S) -> S
    )
}

internal class SetStateImpl<S>(
    defaultRunIf: (S) -> Boolean,
    private val invokeCallback : suspend ( runOnlyIf : (S) -> Boolean, reduce: (S) -> S) -> Unit
) :SetState<S>(defaultRunIf){
    override suspend operator fun invoke(
        runIf: (S) -> Boolean,
        reduce: (currentState: S) -> S
    ) {
        invokeCallback(runIf, reduce)
    }
}

