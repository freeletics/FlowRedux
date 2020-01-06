package com.freeletics.flowredux.dsl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
//import kotlin.test.Test
// import kotlin.test.assertEquals
/*
class FilterStateTest {

    sealed class State {
        object FilterState1 : State()
        object FilterState2 : State()
    }

    class MyStateAccessor(var currentState: State) {
        fun accessor(): State = currentState
    }

   // @Test
    fun filterState() {
        val stateAccessor =
            MyStateAccessor(
                State.FilterState1
            )
        val action1 = ExternalWrappedAction<State, Unit>(Unit)
        val action2 = ExternalWrappedAction<State, Unit>(Unit)


        var recordedValues : List<FilterState.StateChanged>? = null
        GlobalScope.launch(Dispatchers.Main) {
            recordedValues = flowOf(action1, action2)
                .onEach {
                    when(it){
                        action1 -> stateAccessor.currentState = State.FilterState1
                        action2 -> stateAccessor.currentState = State.FilterState2
                    }
                    throw RuntimeException("Fake")
                }
                .filterState(
                    stateAccessor = stateAccessor::accessor,
                    subStateClass = State.FilterState1::class
                )
                .toList()
        }

       // assertEquals(listOf(FilterState.StateChanged.SUBSCRIBE), recordedValues)
    }
}
*/