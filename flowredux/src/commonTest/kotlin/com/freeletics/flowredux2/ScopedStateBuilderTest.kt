package com.freeletics.flowredux2

import app.cash.turbine.test
import com.freeletics.flowredux2.sideeffects.StateChangeCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScopedStateBuilderTest {
    @Test
    fun scopedActionMutatesOnlyTheSelectedState() = runTest {
        val sm = stateMachine(TestState.GenericState("unchanged", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1> {
                        mutate { this + 1 }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("unchanged", 1), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(TestState.GenericState("unchanged", 2), awaitItem())
        }
    }

    @Test
    fun concurrentScopedMutationsUseTheLatestRootState() = runTest {
        val scopedHandlerStarted = CompletableDeferred<Unit>()
        val finishScopedHandler = CompletableDeferred<Unit>()
        val sm = stateMachine(TestState.GenericState("initial", 0)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1>(executionPolicy = ExecutionPolicy.Unordered) {
                        scopedHandlerStarted.complete(Unit)
                        finishScopedHandler.await()
                        mutate { this + 1 }
                    }
                }

                on<TestAction.A2> {
                    mutate { copy(aString = "updated") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            scopedHandlerStarted.await()
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.GenericState("updated", 0), awaitItem())
            finishScopedHandler.complete(Unit)
            assertEquals(TestState.GenericState("updated", 1), awaitItem())
        }
    }

    @Test
    fun concurrentScopedMutationsUseTheLatestScopedState() = runTest {
        val scopedHandlerStarted = CompletableDeferred<Unit>()
        val finishScopedHandler = CompletableDeferred<Unit>()
        val sm = stateMachine(TestState.GenericState("initial", 0)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1> {
                        scopedHandlerStarted.complete(Unit)
                        finishScopedHandler.await()
                        mutate { this + 1 }
                    }
                }

                on<TestAction.A4> { action ->
                    mutate { copy(anInt = action.i) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            scopedHandlerStarted.await()
            sm.dispatchAsync(TestAction.A4(10))
            assertEquals(TestState.GenericState("initial", 10), awaitItem())
            finishScopedHandler.complete(Unit)
            assertEquals(TestState.GenericState("initial", 11), awaitItem())
        }
    }

    @Test
    fun scopedOverrideUsesLatestRootState() = runTest {
        val scopedHandlerStarted = CompletableDeferred<Unit>()
        val finishScopedHandler = CompletableDeferred<Unit>()
        val sm = stateMachine(TestState.GenericState("initial", 0)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1> {
                        scopedHandlerStarted.complete(Unit)
                        finishScopedHandler.await()
                        override { 42 }
                    }
                }

                on<TestAction.A2> {
                    mutate { copy(aString = "updated") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            scopedHandlerStarted.await()
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.GenericState("updated", 0), awaitItem())
            finishScopedHandler.complete(Unit)
            assertEquals(TestState.GenericState("updated", 42), awaitItem())
        }
    }

    @Test
    fun scopedNoChangeDoesNotEmitState() = runTest {
        val handled = CompletableDeferred<Unit>()
        val sm = stateMachine(TestState.GenericState("initial", 0)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1> {
                        handled.complete(Unit)
                        noChange()
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            handled.await()
            expectNoEvents()
        }
    }

    @Test
    fun onEnterAndEffectsReceiveScopedSnapshots() = runTest {
        val snapshots = Channel<String>(Channel.UNLIMITED)
        val sm = stateMachine(TestState.GenericState("value", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.aString },
                    set = { copy(aString = it) },
                ) {
                    onEnter {
                        snapshots.send("enter:$snapshot")
                        mutate { "$this-entered" }
                    }
                    onEnterEffect {
                        snapshots.send("enterEffect:$snapshot")
                    }
                    onActionEffect<TestAction.A1> {
                        snapshots.send("actionEffect:$snapshot")
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("value", 1), awaitItem())
            assertEquals(TestState.GenericState("value-entered", 1), awaitItem())
            sm.dispatch(TestAction.A1)
            expectNoEvents()
        }

        assertEquals(
            setOf("enter:value", "enterEffect:value", "actionEffect:value-entered"),
            buildSet {
                repeat(3) { add(snapshots.receive()) }
            },
        )
    }

    @Test
    fun scopedHandlerIsCancelledWhenLeavingParentState() = runTest {
        val handlerStarted = CompletableDeferred<Unit>()
        val cancellation = CompletableDeferred<Throwable>()
        val sm = stateMachine(TestState.GenericState("initial", 0)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    onActionEffect<TestAction.A1> {
                        handlerStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (throwable: Throwable) {
                            cancellation.complete(throwable)
                            throw throwable
                        }
                    }
                }

                on<TestAction.A2> {
                    override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            handlerStarted.await()
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            assertIs<StateChangeCancellationException>(cancellation.await())
        }
    }

    @Test
    fun scopedConditionCancelsAndRestartsEffects() = runTest {
        val starts = Channel<Int>(Channel.UNLIMITED)
        val cancellations = Channel<Throwable>(Channel.UNLIMITED)
        val sm = stateMachine(TestState.GenericState("initial", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    condition({ it > 0 }) {
                        onEnterEffect {
                            starts.send(snapshot)
                            try {
                                awaitCancellation()
                            } catch (throwable: Throwable) {
                                cancellations.send(throwable)
                                throw throwable
                            }
                        }
                    }
                }

                on<TestAction.A4> { action ->
                    mutate { copy(anInt = action.i) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 1), awaitItem())
            assertEquals(1, starts.receive())
            sm.dispatchAsync(TestAction.A4(0))
            assertEquals(TestState.GenericState("initial", 0), awaitItem())
            assertIs<StateChangeCancellationException>(cancellations.receive())
            sm.dispatchAsync(TestAction.A4(2))
            assertEquals(TestState.GenericState("initial", 2), awaitItem())
            assertEquals(2, starts.receive())
        }
    }

    @Test
    fun scopedIdentityCancelsAndRestartsEffectsOnlyWhenIdentityChanges() = runTest {
        val starts = Channel<Int>(Channel.UNLIMITED)
        val cancellations = Channel<Throwable>(Channel.UNLIMITED)
        val sm = stateMachine(TestState.GenericState("initial", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    untilIdentityChanges({ it }) {
                        onEnterEffect {
                            starts.send(snapshot)
                            try {
                                awaitCancellation()
                            } catch (throwable: Throwable) {
                                cancellations.send(throwable)
                                throw throwable
                            }
                        }
                    }
                }

                on<TestAction.A2> {
                    mutate { copy(aString = "updated") }
                }
                on<TestAction.A4> { action ->
                    mutate { copy(anInt = action.i) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("initial", 1), awaitItem())
            assertEquals(1, starts.receive())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.GenericState("updated", 1), awaitItem())
            assertTrue(starts.tryReceive().isFailure)
            assertTrue(cancellations.tryReceive().isFailure)
            sm.dispatchAsync(TestAction.A4(2))
            assertEquals(TestState.GenericState("updated", 2), awaitItem())
            assertIs<StateChangeCancellationException>(cancellations.receive())
            assertEquals(2, starts.receive())
        }
    }

    @Test
    fun conditionAndFlowBuilderUseTheScopedState() = runTest {
        val values = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val sm = stateMachine(TestState.GenericState("unchanged", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    condition({ it > 0 }) {
                        collectWhileInState({ values }) { value ->
                            mutate { this + value }
                        }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("unchanged", 1), awaitItem())
            values.emit(2)
            assertEquals(TestState.GenericState("unchanged", 3), awaitItem())
        }
    }

    @Test
    fun directFlowAndBothEffectOverloadsUseScopedState() = runTest {
        val effectValues = Channel<String>(Channel.UNLIMITED)
        val directEffectFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val builderEffectFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val sm = stateMachine(TestState.GenericState("unchanged", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    collectWhileInState(flowOf(2)) { value ->
                        mutate { this + value }
                    }
                    collectWhileInStateEffect(directEffectFlow) { value ->
                        effectValues.send("direct:$snapshot:$value")
                    }
                    collectWhileInStateEffect({ initial -> builderEffectFlow }) { value ->
                        effectValues.send("builder:$snapshot:$value")
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("unchanged", 1), awaitItem())
            assertEquals(TestState.GenericState("unchanged", 3), awaitItem())
            directEffectFlow.emit(4)
            builderEffectFlow.emit(5)
            expectNoEvents()
        }

        assertEquals(setOf("direct:3:4", "builder:3:5"), setOf(effectValues.receive(), effectValues.receive()))
    }

    @Test
    fun actionsAreFilteredAndBroadcastToIndependentScopes() = runTest {
        val handledA1 = Channel<Unit>(Channel.UNLIMITED)
        val sm = stateMachine(TestState.GenericState("a", 1)) {
            inState<TestState.GenericState> {
                inScopedState(
                    get = { it.aString },
                    set = { copy(aString = it) },
                ) {
                    on<TestAction.A1> {
                        handledA1.send(Unit)
                        mutate { "$this!" }
                    }
                }
                inScopedState(
                    get = { it.anInt },
                    set = { copy(anInt = it) },
                ) {
                    on<TestAction.A1> {
                        handledA1.send(Unit)
                        mutate { this + 1 }
                    }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.GenericState("a", 1), awaitItem())
            sm.dispatch(TestAction.A2)
            expectNoEvents()
            sm.dispatchAsync(TestAction.A1)
            handledA1.receive()
            handledA1.receive()
            val first = awaitItem()
            val second = awaitItem()
            assertTrue(listOf(first, second).contains(TestState.GenericState("a!", 2)))
        }
    }
}
