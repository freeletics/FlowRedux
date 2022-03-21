package com.freeletics.flowredux.dsl.util

import java.util.concurrent.atomic.AtomicInteger

internal actual class AtomicCounter actual constructor(initialValue: Int) {

    private val atomicInt: AtomicInteger = AtomicInteger(initialValue)

    internal actual fun get(): Int = atomicInt.get()
    internal actual fun incrementAndGet(): Int = atomicInt.incrementAndGet()
    internal actual fun decrementAndGet(): Int = atomicInt.decrementAndGet()
}
