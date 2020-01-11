package com.freeletics.flowredux

import timber.log.Timber

object AndroidFlowReduxLogger : FlowReduxLogger {
    override fun log(message: String) {
        Timber.tag("FlowRedux").d(message)
    }
}