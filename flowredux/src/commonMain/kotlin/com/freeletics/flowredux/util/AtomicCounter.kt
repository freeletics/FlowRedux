package com.freeletics.flowredux.util

internal expect class AtomicCounter(initialValue: Int) {
    internal fun get(): Int

    internal fun incrementAndGet(): Int

    internal fun decrementAndGet(): Int
}
