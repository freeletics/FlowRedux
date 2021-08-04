package com.freeletics.flowredux.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun StateMachine(
        builderBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
): TestStateMachine {
    return TestStateMachine(builderBlock)
}
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TestStateMachine  constructor(
    val specBlock: FlowReduxStoreBuilder<TestState, TestAction>.() -> Unit
) {

    private val inputActions = Channel<TestAction>()

    val state: Flow<TestState>
        get() {
        return inputActions
            .consumeAsFlow()
            .reduxStore(CommandLineLogger, TestState.Initial, specBlock)
            .flowOn(Dispatchers.Default)
        }

    suspend fun dispatch(action: TestAction) {
        inputActions.send(action)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun dispatchAsync(action: TestAction) {
        GlobalScope.launch {
            dispatch(action)
        }
    }
}
