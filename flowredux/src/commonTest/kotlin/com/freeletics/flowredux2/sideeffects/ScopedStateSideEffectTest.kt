package com.freeletics.flowredux2.sideeffects

import com.freeletics.flowredux2.ChangedState
import com.freeletics.flowredux2.NoStateChange
import com.freeletics.flowredux2.TestAction
import com.freeletics.flowredux2.TestState
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScopedStateSideEffectTest {
    @Test
    fun projectionIsCancelledIfParentStateChangedBeforeReadingCurrentState() = runTest {
        val scopedBuilder = SideEffectBuilder<Int, Int, TestAction>(
            isInState = { true },
            logger = null,
        ) {
            object : SideEffect<Int, Int, TestAction>() {
                override val isInState = IsInState<Int> { true }
                override val logger = null

                override fun produceState(getState: GetState<Int>): Flow<ChangedState<Int>> = flow {
                    getState()
                    emit(NoStateChange)
                }
            }
        }
        val parentBuilder = scopedBuilder.inScopedState<TestState.GenericState, TestState, TestAction, Int>(
            parentIsInState = { it is TestState.GenericState },
            get = { it.anInt },
            set = { copy(anInt = it) },
        )
        val sideEffect = parentBuilder.build(TestState.GenericState("initial", 1))

        val exception = runCatching {
            sideEffect.produceState { TestState.S2 }.toList()
        }.exceptionOrNull()

        assertIs<StateChangeCancellationException>(exception)
    }
}
