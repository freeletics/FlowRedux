package com.freeletics.flowredux.dsl

sealed class Action {
    object A1 : Action()
    object A2 : Action()
}

sealed class State {
    object Initial : State()
    object S1 : State()
    object S2 : State()
    object S3 : State()

    data class GenericState(val aString : String, val anInt : Int) : State()
}