package com.freeletics.flowredux.dsl

sealed class TestAction {
    object A1 : TestAction()
    object A2 : TestAction()
    object A3 : TestAction()
}

sealed class TestState {
    object Initial : TestState()
    object S1 : TestState()
    object S2 : TestState()
    object S3 : TestState()

    data class GenericState(val aString : String, val anInt : Int) : TestState()
}
