package com.freeletics.flowredux2

internal sealed class TestAction {
    object A1 : TestAction()

    object A2 : TestAction()

    object A3 : TestAction()

    data class A4(val i: Int) : TestAction()
}

internal sealed class TestState {
    object Initial : TestState()

    object S1 : TestState()

    object S2 : TestState()

    object S3 : TestState()

    data class GenericState(val aString: String, val anInt: Int) : TestState()

    data class GenericNullableState(val aString: String?, val anInt: Int?) : TestState()

    data class CounterState(val counter: Int) : TestState()
}
