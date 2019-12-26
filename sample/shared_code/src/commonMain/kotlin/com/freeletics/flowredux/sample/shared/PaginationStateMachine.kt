package com.freeletics.flowredux.sample.shared

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.dsl.FlowReduxStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class Action

sealed class PaginationState

object LoadingPaginationState : PaginationState()
object FooAction : Action()

internal class InternalPaginationStateMachine(logger: FlowReduxLogger) :
    FlowReduxStateMachine<PaginationState, Action>(logger, LoadingPaginationState, {

    })



class PaginationStateMachine(
    logger: FlowReduxLogger,
    private val scope: CoroutineScope,
    private val stateChangeListener: (PaginationState) -> Unit
) {
    private val stateMachine = InternalPaginationStateMachine(logger)

    init {
        scope.launch {
            stateMachine.state.collect {
                stateChangeListener(it)
            }
        }
    }

    fun dispatch(action: Action) {
        scope.launch {
            stateMachine.dispatch(action)
        }
    }
}

