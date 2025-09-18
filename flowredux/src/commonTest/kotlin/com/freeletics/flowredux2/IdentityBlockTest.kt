package com.freeletics.flowredux2

import app.cash.turbine.test
import com.freeletics.flowredux2.sideeffects.StateChangeCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class IdentityBlockTest {
    @Test
    fun blockStartsWheneverIdentityChanges() = runTest {
        val signal = Channel<Unit>(capacity = Int.MAX_VALUE)

        val gs1 = TestState.GenericState("asd", 1)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnterEffect {
                        signal.send(Unit)
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(anInt = anInt + 1) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 2), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 3), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 4), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 5), awaitItem())
            signal.receive()
        }
    }

    @Test
    fun blockDoesNotStartAgainIfIdentityDoesNotChange() = runTest {
        var counter = 0

        val gs1 = TestState.GenericState("asd", 1)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnterEffect {
                        counter++
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(aString = aString + "1") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd1"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd11"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd111"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd1111"), awaitItem())
        }

        assertEquals(1, counter)
    }

    @Test
    fun blockIsCancelledIfIdentityChanges() = runTest {
        val signal = Channel<Unit>()
        val cancellations = mutableListOf<Pair<Int, Throwable>>()

        val gs1 = TestState.GenericState("asd", 1)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnter {
                        try {
                            signal.send(Unit)
                            awaitCancellation()
                        } catch (t: Throwable) {
                            cancellations.add(snapshot.anInt to t)
                            throw t
                        }
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(anInt = anInt + 1) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 2), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 3), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 4), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 5), awaitItem())

            assertEquals(4, cancellations.size)
            signal.receive()
        }

        assertEquals(4, cancellations.size)
        assertEquals(1, cancellations[0].first)
        assertIs<StateChangeCancellationException>(cancellations[0].second)
        assertEquals(2, cancellations[1].first)
        assertIs<StateChangeCancellationException>(cancellations[1].second)
        assertEquals(3, cancellations[2].first)
        assertIs<StateChangeCancellationException>(cancellations[2].second)
        assertEquals(4, cancellations[3].first)
        assertIs<StateChangeCancellationException>(cancellations[3].second)
    }

    @Test
    fun blockIsNotCancelledIfIdentityDoesNotChange() = runTest {
        val cancellations = mutableListOf<Pair<Int, Throwable>>()

        val gs1 = TestState.GenericState("asd", 1)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnter {
                        try {
                            awaitCancellation()
                        } catch (t: Throwable) {
                            cancellations.add(snapshot.anInt to t)
                            throw t
                        }
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(aString = aString + "1") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd1"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd11"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd111"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "asd1111"), awaitItem())

            assertEquals(0, cancellations.size)
        }

        assertEquals(0, cancellations.size)
    }

    @Test
    fun blockStartsWhenIdentityChangesBetweenNullAndNotNull() = runTest {
        val signal = Channel<Unit>(capacity = Int.MAX_VALUE)

        val gs1 = TestState.GenericNullableState(null, null)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericNullableState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnterEffect {
                        signal.send(Unit)
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(anInt = (anInt ?: 0) + 1) }
                }

                on<TestAction.A2> {
                    mutate { copy(anInt = null) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 1), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A2)
            assertEquals(gs1, awaitItem())
            signal.receive()
        }
    }

    @Test
    fun blockDoesNotStartAgainIfIdentityStaysNull() = runTest {
        var counter = 0

        val gs1 = TestState.GenericNullableState(null, null)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericNullableState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnterEffect {
                        counter++
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(aString = aString + "1") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null1"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null11"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null111"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null1111"), awaitItem())
        }

        assertEquals(1, counter)
    }

    @Test
    fun blockIsCancelledIfIdentityChangesBetweenNullAndNotNull() = runTest {
        val signal = Channel<Unit>()
        val cancellations = mutableListOf<Pair<Int?, Throwable>>()

        val gs1 = TestState.GenericNullableState(null, null)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericNullableState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnter {
                        try {
                            signal.send(Unit)
                            awaitCancellation()
                        } catch (t: Throwable) {
                            cancellations.add(snapshot.anInt to t)
                            throw t
                        }
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(anInt = (anInt ?: 0) + 1) }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 1), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 2), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 3), awaitItem())
            signal.receive()
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(anInt = 4), awaitItem())

            assertEquals(4, cancellations.size)
            signal.receive()
        }

        assertEquals(4, cancellations.size)
        assertEquals(null, cancellations[0].first)
        assertIs<StateChangeCancellationException>(cancellations[0].second)
        assertEquals(1, cancellations[1].first)
        assertIs<StateChangeCancellationException>(cancellations[1].second)
        assertEquals(2, cancellations[2].first)
        assertIs<StateChangeCancellationException>(cancellations[2].second)
        assertEquals(3, cancellations[3].first)
        assertIs<StateChangeCancellationException>(cancellations[3].second)
    }

    @Test
    fun blockIsNotCancelledIfIdentityStaysNull() = runTest {
        val cancellations = mutableListOf<Pair<Int?, Throwable>>()

        val gs1 = TestState.GenericNullableState(null, null)

        val sm = stateMachine {
            inState<TestState.Initial> {
                on<TestAction.A1> {
                    override { gs1 }
                }
            }

            inState<TestState.GenericNullableState> {
                untilIdentityChanges({ it.anInt }) {
                    onEnter {
                        try {
                            awaitCancellation()
                        } catch (t: Throwable) {
                            cancellations.add(snapshot.anInt to t)
                            throw t
                        }
                    }
                }

                on<TestAction.A1> {
                    mutate { copy(aString = aString + "1") }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null1"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null11"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null111"), awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertEquals(gs1.copy(aString = "null1111"), awaitItem())

            assertEquals(0, cancellations.size)
        }

        assertEquals(0, cancellations.size)
    }
}
