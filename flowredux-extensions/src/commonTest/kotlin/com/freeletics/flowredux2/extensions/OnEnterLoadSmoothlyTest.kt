package com.freeletics.flowredux2.extensions

import app.cash.burst.Burst
import app.cash.turbine.Turbine
import app.cash.turbine.test
import com.freeletics.flowredux2.FlowReduxStateMachineFactory
import com.freeletics.flowredux2.initializeWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@Burst
@OptIn(ExperimentalCoroutinesApi::class)
class OnEnterLoadSmoothlyTest(
    val stateMachine: StateMachine = StateMachine.REGULAR,
) {
    @Test
    fun `loading takes less than 500ms - state is immediately emitted`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = stateMachine.create(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), stateMachine.loadingState(false))
            // send result after 400ms
            timeSource.advanceTimeBy(400.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), stateMachine.successState("Success"))
            // no further updates after complete delay ended
            timeSource.advanceTimeBy(600.milliseconds)
        }
    }

    @Test
    fun `loading takes between 500 and 1000ms - show is updated and result is delayed`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = stateMachine.create(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), stateMachine.loadingState(false))
            // after 500ms and not receiving a result show is updated
            timeSource.advanceTimeBy(500.milliseconds)
            assertEquals(awaitItem(), stateMachine.loadingState(true))
            // send result will not trigger any emissions yet
            resultSignal.add(Unit)
            expectNoEvents()
            // still no emissions right before second delay ends
            timeSource.advanceTimeBy(499.milliseconds)
            expectNoEvents()
            // reaching second delay emits result
            timeSource.advanceTimeBy(1.milliseconds)
            assertEquals(awaitItem(), stateMachine.successState("Success"))
        }
    }

    @Test
    fun `loading takes over 1000ms - show was updated and result is immediately disabled`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = stateMachine.create(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), stateMachine.loadingState(false))
            // after 500ms and not receiving a result show is updated
            timeSource.advanceTimeBy(500.milliseconds)
            assertEquals(awaitItem(), stateMachine.loadingState(true))
            // when result is available right after second delay is over it's immediately emitted
            timeSource.advanceTimeBy(501.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), stateMachine.successState("Success"))
        }
    }

    @Test
    fun `no initial delay - loading takes less than 500ms - result delivered after minimum loading time`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = stateMachine.create(resultSignal, timeSource, true)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), stateMachine.loadingState(true))
            // result is delivered within delay but state not updated until minimum loading time is reached
            timeSource.advanceTimeBy(300.milliseconds)
            resultSignal.add(Unit)
            expectNoEvents()
            // state is updated after reaching minimum loading time
            timeSource.advanceTimeBy(200.milliseconds)
            assertEquals(awaitItem(), stateMachine.successState("Success"))
        }
    }

    @Test
    fun `no initial delay - loading takes over 500ms - result delivered immediately`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = stateMachine.create(resultSignal, timeSource, true)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), stateMachine.loadingState(true))
            // after 500ms nothing is updated
            timeSource.advanceTimeBy(500.milliseconds)
            expectNoEvents()
            // deliver result right after minimum loading time updates state
            timeSource.advanceTimeBy(1.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), stateMachine.successState("Success"))
        }
    }
}

enum class StateMachine {
    REGULAR,
    WITH_LOADING_STATE,
}

@OptIn(ExperimentalCoroutinesApi::class)
fun StateMachine.create(resultSignal: Turbine<Unit>, timeSource: TimeSource.WithComparableMarks, defaultShowLoadingIndicator: Boolean = false): FlowReduxStateMachineFactory<*, *> = when (this) {
    StateMachine.REGULAR -> TestStateMachine(resultSignal, timeSource, TestState.Loading(defaultShowLoadingIndicator))
    StateMachine.WITH_LOADING_STATE -> LoadingTestStateMachine(resultSignal, timeSource, LoadingTestState.Loading(defaultShowLoadingIndicator))
}

@OptIn(ExperimentalCoroutinesApi::class)
fun StateMachine.loadingState(showLoadingIndicator: Boolean): Any = when (this) {
    StateMachine.REGULAR -> TestState.Loading(showLoadingIndicator)
    StateMachine.WITH_LOADING_STATE -> LoadingTestState.Loading(showLoadingIndicator)
}

@OptIn(ExperimentalCoroutinesApi::class)
fun StateMachine.successState(value: String): Any = when (this) {
    StateMachine.REGULAR -> TestState.Success(value)
    StateMachine.WITH_LOADING_STATE -> LoadingTestState.Success(value)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestStateMachine(
    resultSignal: Turbine<Unit>,
    timeSource: TimeSource.WithComparableMarks,
    initialState: TestState,
) : FlowReduxStateMachineFactory<TestState, Any>() {
    init {
        spec {
            initializeWith { initialState }

            inState<TestState.Loading> {
                onEnterLoadSmoothly(
                    startShowingLoadingIndicator = { copy(showLoadingIndicator = true) },
                    shouldDelayLoadingIndicator = { !showLoadingIndicator },
                    timeSource = timeSource,
                ) {
                    resultSignal.awaitItem()
                    override { TestState.Success("Success") }
                }
            }
        }
    }
}

sealed class TestState {
    data class Loading(val showLoadingIndicator: Boolean) : TestState()

    data class Success(val value: Any) : TestState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoadingTestStateMachine(
    resultSignal: Turbine<Unit>,
    timeSource: TimeSource.WithComparableMarks,
    initialState: LoadingTestState,
) : FlowReduxStateMachineFactory<LoadingTestState, Any>() {
    init {
        spec {
            initializeWith { initialState }

            inState<LoadingTestState.Loading> {
                onEnterLoadSmoothly(timeSource = timeSource) {
                    resultSignal.awaitItem()
                    override { LoadingTestState.Success("Success") }
                }
            }
        }
    }
}

sealed class LoadingTestState {
    data class Loading(override val showLoadingIndicator: Boolean) : LoadingTestState(), LoadingState<Loading> {
        override fun withShowLoadingIndicatorEnabled(): LoadingTestState.Loading = copy(showLoadingIndicator = true)
    }

    data class Success(val value: Any) : LoadingTestState()
}
