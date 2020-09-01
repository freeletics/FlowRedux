package com.freeletics.flowredux

import app.cash.turbine.test
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class FlowReduxTest2 {

    @ExperimentalTime
    @Test
    fun turbine() = suspendTest {
        //GlobalScope.launch {
        flowOf(1, 2, 3).test {
            assertEquals(10, expectItem())
        }
        //}
    }
}