package com.freeletics.flowredux.dsl.internal

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlin.jvm.JvmInline

/**
 * This is a simple synchronization mechanism that allows coroutines to suspend and wait
 * until another coroutine is explicitly calling #resume(). Then all coroutines who were susupending
 * and waiting because called #waitUntilResumed() will resume.
 *
 * A [CoroutineWaiter] always starts in "waiting" mode meaning, that you explicitly have to
 * call #resume() to continue any waiting coroutines (who have called #waitUntilResumed() before).
 *
 * Once a [CoroutineWaiter] has reached the "resumed" state by at least one
 * invocation of #resume(), it is not possible to go back to the "waiting" state.
 * To do that you need to create a new [CoroutineWaiter] instance.
 *
 * The reason why we are not using simple coroutines primitives directly but instead wrap them
 * in a [CoroutineWaiter] class is to ensure more readable FlowRedux internal code and easier
 * changes of the implementation in the future.
 *
 * [CoroutineWaiter] is mainly used to ensure not to dispatch any action to sub statemachines before
 * the corresponding statemachine's state is collected.
 */
@JvmInline
internal value class CoroutineWaiter(private val job: CompletableJob = Job()) {
    /**
     * Suspends the current coroutine (that calls this method) until #resume()
     * is called (by another coroutine).
     */
    suspend inline fun waitUntilResumed() {
        job.join()
    }

    /**
     * Resumes any waiting coroutine that has been suspended by calling #waitUntilResumed().
     *
     * Calling this method multiple times has no effect. Once resumed, it stays resumed forever.
     */
    fun resume() {
        job.complete()
    }

    fun isResumed(): Boolean {
        return job.isCompleted
    }
}
