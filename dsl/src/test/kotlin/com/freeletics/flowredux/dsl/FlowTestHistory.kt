package com.freeletics.flowredux.dsl

import kotlinx.coroutines.flow.Flow
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.ReplaySubject
import kotlinx.coroutines.rx2.asObservable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert

/**
 * Use [Observable.com.freeletics.flowredux.dsl.testOverTime] to create a new instance.
 *
 * A [FlowEmissionHistory] keeps track of all emitted values over time.
 * Thus, while writing a test as a developer you can only verify the last emitted value while under the hood
 * the whole history of all emitted items is verified. This allows the developer to write less code.
 *
 * Use [FlowEmissionHistory.shouldEmitNext] to verify emissions over time.
 * Use [FlowEmissionHistory.shouldNotEmitMoreValues] to verify that no more items are emitted over time (or
 * until [FlowEmissionHistory.dispose] is called.
 *
 * ```
 * val stateObserver = stateMachine.state.com.freeletics.flowredux.dsl.testOverTime()
 *
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Loading
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Content(list("FirstItem", "SecondItem"))
 * ```
 *
 * Also check [FlowEmissionHistory.shouldNotHaveEmittedSinceLastCheck] and
 * [FlowEmissionHistory.shouldNotEmitMoreValues] for additional assertions.
 *
 * Internally we don't implement onComplete and onError on purpose. The reason is that this class is only about
 * checking emissions but not about throwing errors or completion terminal events. If you want to verify that behavior
 * (rather than emissions only) use [Observable.test] and [io.reactivex.observers.TestObserver] instead.
 *
 */
class FlowEmissionHistory<T> internal constructor(
    private val timeoutConfig: TimeoutConfig,
    observable: Observable<T>
) : Disposable {

    private val readLock = Any()
    private val emitLock = Any()

    /**
     * This field is set to null if [disposeAndCleanUp] has been called.
     * So to check if cleanup has been called you can check if this field is null.
     */
    internal var replayHistory: ReplaySubject<T>? = ReplaySubject.create<T>()

    internal var verifiedHistory: List<T> = emptyList()

    internal val noMoreEmissions = AtomicBoolean(false)

    var exception: Exception? = null
    var completed: Boolean = false

    private val onNextConsumer = Consumer<T> { item ->
        checkNoMoreEmissions(item)
        val replay = replayHistory
        if (replay == null) {
            checkNoMoreEmissions(item)
            throw IllegalStateException(
                "${FlowEmissionHistory::class.java.simpleName} is already cleanedUp " +
                    " but received new emission: $item"
            )
        } else {
            synchronized(emitLock) {
                // ReplaySubject is not ThreadSafe
                replay.onNext(item)
            }
        }
    }

    // TODO error handling? next PR.
    private val disposable: Disposable = observable.subscribe(onNextConsumer)

    /**
     * @return true if disposed otherwise false
     */
    override fun isDisposed(): Boolean = disposable.isDisposed

    /**
     * Disposes the subscription for the original [Observable]
     */
    override fun dispose() {
        disposable.dispose()
    }

    /**
     * Clean up the internal memory by disposing the original observable,
     * and clear the history of emissions.
     * This prevents high memory usage over multiple test cases. This method should be called at the end of each
     * test to prevent OutOfMemoryError.
     */
    fun disposeAndCleanUp() {
        synchronized(readLock) {
            dispose()
            replayHistory = null // release resources
            verifiedHistory = emptyList() // release resources
        }
    }

    /**
     * Checks if [shouldNotEmitMoreValues] has been called. If so and a new items is emitted a [IllegalStateException]
     * is thrown causing the test to fail. This method is for internal usage only.
     */
    private fun checkNoMoreEmissions(item: T) {
        if (noMoreEmissions.get()) {
            throw IllegalStateException(
                "No more emissions expected " +
                    "because ${FlowEmissionHistory::class.java.simpleName}.shouldNotEmitMoreValues() has " +
                    "been called but received new emission: $item"
            )
        }
    }

    /**
     * Checks if the passed value(s) has (have) been emitted by the subscribed observable.
     * This is a blocking call meaning it either
     *
     * - awaits for the next value to be emitted (or timeout if it takes longer
     * than expected, see [testOverTime])
     * - or in case items has been emitted before this method has been called it records the emitted item and checks the
     * recorded emission(s) with the
     *
     * @param nextEmissions vararg of items to check for emission.
     */
    fun shouldEmitNext(vararg nextEmissions: T) {
        // TODO refactor this to standalone class. Next PR
        // TODO move timeout config from constructor to method parameter
        synchronized(readLock) {
            checkDisposeAndCleanupCalled()

            val expectedEmissions = verifiedHistory + nextEmissions

            val actualEmissions = replayHistory!!
                .take(expectedEmissions.size.toLong())
                .timeout(
                    timeoutConfig.timeout,
                    timeoutConfig.timeoutTimeUnit,
                    timeoutConfig.timeoutScheduler
                )
                .toList()
                .blockingGet()

            Assert.assertEquals(
                "Expected emissions is different from actual emissions",
                expectedEmissions,
                actualEmissions
            )
            verifiedHistory = actualEmissions
        }
    }

    /**
     * Verifies that no more items are emitted over time (until [disposeAndCleanUp] has been called)
     */
    fun shouldNotEmitMoreValues() {
        synchronized(readLock) {
            val replayValues = replayHistory?.values?.toList()
                ?: throw IllegalStateException(
                    "Already called .dispose() which cleans up resource incl. " +
                        "all history of emissions. Thus this method cant be called anymore"
                )
            when {
                verifiedHistory.size < replayValues.size -> {
                    // We have unverified emissions
                    Assert.fail(
                        "Unverified items detected that you have never checked before by " +
                            "calling .com.freeletics.flowredux.dsl.shouldEmitNext(...)." +
                            "\nVerified: $verifiedHistory" +
                            "\nAll emitted items: $replayValues" +
                            "\nUnverified items emitted after last .com.freeletics.flowredux.dsl.shouldEmitNext() call: ${replayValues.subList(
                                verifiedHistory.size, replayValues.size
                            )}"
                    )
                }
                verifiedHistory.size > replayValues.size -> {
                    throw IllegalStateException(
                        "Oops, verified history contains more emissions than actually recorded?!?" +
                            "That's not possible, we run into a bug. Please file a bug."
                    )
                }

                verifiedHistory.size == replayValues.size -> {
                    // All good. No unchecked emissions detected. Additionally, mark that we expect no more emissions
                    // and fail in case we do get more emissions from source.
                    noMoreEmissions.set(true)
                }
            }
        }
    }

    /**
     * Verifies that no item has been emittes since last time we checked for emissions via [shouldEmitNext]
     */
    fun shouldNotHaveEmittedSinceLastCheck(timeoutConfig: TimeoutConfig? = null) {
        synchronized(readLock) {
            checkDisposeAndCleanupCalled()
            if (timeoutConfig != null) {
                Observable.timer(
                    timeoutConfig.timeout,
                    timeoutConfig.timeoutTimeUnit,
                    timeoutConfig.timeoutScheduler
                )
                    .blockingFirst()
            }
            Assert.assertEquals(verifiedHistory, replayHistory!!.values.toList())
        }
    }

    private fun checkDisposeAndCleanupCalled() {
        if (replayHistory == null) {
            throw IllegalStateException(
                "Already cleaned up. " +
                    "Not possible to call this method after .disposeAndCleanUp()"
            )
        }
    }
}

/**
 * A data class that wraps all needed attributes to configure timouts
 */
data class TimeoutConfig(
    /**
     * How long should the timeout be
     */
    val timeout: Long,

    /**
     * What is the [TimeUnit] the timeout parameter refers to?
     */
    val timeoutTimeUnit: TimeUnit,

    /**
     * Which [Scheduler] should the timeout timer run on?
     */
    val timeoutScheduler: Scheduler = Schedulers.computation()
) {
    companion object {
        fun default(): TimeoutConfig =
            TimeoutConfig(
                timeout = 2,
                timeoutTimeUnit = TimeUnit.SECONDS,
                timeoutScheduler = Schedulers.computation()
            )
    }
}

/**
 * Returns an [FlowEmissionHistory] to fluently check for rx emissions over time.
 *
 * ```
 * val stateObserver = stateMachine.state.com.freeletics.flowredux.dsl.testOverTime()
 *
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Loading
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Content(list("FirstItem", "SecondItem"))
 * ```
 *
 * Also check [FlowEmissionHistory.shouldNotHaveEmittedSinceLastCheck] and
 * [FlowEmissionHistory.shouldNotEmitMoreValues] for additional assertions.
 *
 * @param timeoutConfig Defines the com.freeletics.flowredux.dsl.TimeoutConfig
 *
 * @see FlowEmissionHistory
 */
fun <T : Any> Flow<T>.testOverTime(
    timeoutConfig: TimeoutConfig = TimeoutConfig.default()
): FlowEmissionHistory<T> =
    FlowEmissionHistory(
        timeoutConfig = timeoutConfig,
        observable = this.asObservable()
/*
    Observable.create { emitter ->
        val flow = this
        try {
            runBlocking {
                flow.collect {
                    if (!emitter.isDisposed) {
                        emitter.onNext(it)
                    }
                }
            }
            emitter.onComplete()
        } catch (t: Throwable) {
            emitter.onError(t)
        }
    }
 */
    )

/**
 * Simple infix function to check what the next emission is.
 * It stores all emitted items internally in a "history" and with every new call of this method it checks the full
 * history of emissions plus the passed expected item (see method parameter) to match with the actual emission over
 * time. In case the expected item hasn't been emitted yet it waits for this emission or will fail if next emitted item
 * is not the expected one or timeout has been reached (while waiting for next emission). In case the item has been
 * emitted before calling this method handels this case too (queues emissions internally until next call of this
 * method).
 *
 * ```
 * val stateObserver = stateMachine.state.com.freeletics.flowredux.dsl.testOverTime()
 *
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Loading
 * stateObserver com.freeletics.flowredux.dsl.shouldEmitNext State.Content(list("FirstItem", "SecondItem"))
 * ```
 *
 *
 * @param item The expected emitted item that should be emitted next
 * @see FlowEmissionHistory.shouldEmitNext
 */
infix fun <T> FlowEmissionHistory<T>.shouldEmitNext(item: T) {
    this.shouldEmitNext(item)
}