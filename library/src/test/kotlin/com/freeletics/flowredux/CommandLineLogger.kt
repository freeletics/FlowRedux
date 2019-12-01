package com.freeletics.flowredux

internal object CommandLineLogger : FlowReduxLogger{

    override fun log(message: String) {
        println(message)
    }
}
