package com.freeletics.flowredux.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

internal class CoroutineWaiterTest {

    @Test
    fun waitUntilResumedAndOnceResumedDontWaitInTheFutureAnymore() = runTest {
        withContext(Dispatchers.Unconfined) { // We want real delay() here, thus using this Dispatcher
            var reached1 = false
            var reached2 = false
            val waiter = CoroutineWaiter()
            val job1 = launch {
                assertFalse(reached1)
                assertFalse(waiter.isResumed())
                waiter.waitUntilResumed()

                reached1 = true
            }

            val job2 = launch {
                assertFalse(reached2)
                assertFalse(waiter.isResumed())
                waiter.waitUntilResumed()

                reached2 = true
                val job3 = launch {
                    assertTrue(waiter.isResumed())
                    waiter.waitUntilResumed()
                    assertTrue(waiter.isResumed())
                }
                job3.join()
            }

            val releaseJob = launch {
                delay(20)
                assertFalse(reached1)
                assertFalse(reached2)
                assertFalse(waiter.isResumed())
                waiter.resume()
                assertTrue(waiter.isResumed())
            }

            joinAll(job1, job2, releaseJob)
            assertTrue(reached1)
            assertTrue(reached2)
        }
    }
}
