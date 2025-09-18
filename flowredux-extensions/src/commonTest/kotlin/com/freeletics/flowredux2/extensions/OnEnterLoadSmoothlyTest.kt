package com.freeletics.flowredux2.extensions

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

class OnEnterLoadSmoothlyTest {
    @Test
    fun `loading takes less than 500ms - state is immediately emitted`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = TestStateMachine(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), TestState.Loading(false))
            // send result after 400ms
            timeSource.advanceTimeBy(400.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), TestState.Success("Success"))
            // no further updates after complete delay ended
            timeSource.advanceTimeBy(600.milliseconds)
        }
    }

    @Test
    fun `loading takes between 500 and 1000ms - show is updated and result is delayed`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = TestStateMachine(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), TestState.Loading(false))
            // after 500ms and not receiving a result show is updated
            timeSource.advanceTimeBy(500.milliseconds)
            assertEquals(awaitItem(), TestState.Loading(true))
            // send result will not trigger any emissions yet
            resultSignal.add(Unit)
            expectNoEvents()
            // still no emissions right before second delay ends
            timeSource.advanceTimeBy(499.milliseconds)
            expectNoEvents()
            // reaching second delay emits result
            timeSource.advanceTimeBy(1.milliseconds)
            assertEquals(awaitItem(), TestState.Success("Success"))
        }
    }

    @Test
    fun `loading takes over 1000ms - show was updated and result is immediately disabled`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = TestStateMachine(resultSignal, timeSource)

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), TestState.Loading(false))
            // after 500ms and not receiving a result show is updated
            timeSource.advanceTimeBy(500.milliseconds)
            assertEquals(awaitItem(), TestState.Loading(true))
            // when result is available right after second delay is over it's immediately emitted
            timeSource.advanceTimeBy(501.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), TestState.Success("Success"))
        }
    }

    @Test
    fun `no initial delay - loading takes less than 500ms - result delivered after minimum loading time`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = TestStateMachine(resultSignal, timeSource, TestState.Loading(true))

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), TestState.Loading(true))
            // result is delivered within delay but state not updated until minimum loading time is reached
            timeSource.advanceTimeBy(300.milliseconds)
            resultSignal.add(Unit)
            expectNoEvents()
            // state is updated after reaching minimum loading time
            timeSource.advanceTimeBy(200.milliseconds)
            assertEquals(awaitItem(), TestState.Success("Success"))
        }
    }

    @Test
    fun `no initial delay - loading takes over 500ms - result delivered immediately`() = runTest {
        val resultSignal = Turbine<Unit>()
        val timeSource = TestTimeSource()
        val underTest = TestStateMachine(resultSignal, timeSource, TestState.Loading(true))

        underTest.shareIn(backgroundScope).state.test {
            // initial state
            assertEquals(awaitItem(), TestState.Loading(true))
            // after 500ms nothing is updated
            timeSource.advanceTimeBy(500.milliseconds)
            expectNoEvents()
            // deliver result right after minimum loading time updates state
            timeSource.advanceTimeBy(1.milliseconds)
            resultSignal.add(Unit)
            assertEquals(awaitItem(), TestState.Success("Success"))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestStateMachine(
    resultSignal: Turbine<Unit>,
    timeSource: TimeSource.WithComparableMarks,
    initialState: TestState = TestState.Loading(false),
) : FlowReduxStateMachineFactory<TestState, Any>() {
    init {
        spec {
            initializeWith { initialState }

            inState<TestState.Loading> {
                onEnterLoadSmoothly(
                    startShowingLoadingIndicator = { copy(show = true) },
                    shouldDelayLoadingIndicator = { !show },
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
    data class Loading(val show: Boolean) : TestState()

    data class Success(val value: Any) : TestState()
}
