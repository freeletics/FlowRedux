package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger

object CommandLineLogger : FlowReduxLogger {
    override fun log(message: String) {
        println(message)
    }
}
