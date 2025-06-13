package com.freeletics.flowredux2.util

import java.util.concurrent.atomic.AtomicInteger

internal actual class AtomicCounter actual constructor(initialValue: Int) {
    private val atomicInt: AtomicInteger = AtomicInteger(initialValue)

    actual fun get(): Int = atomicInt.get()

    actual fun incrementAndGet(): Int = atomicInt.incrementAndGet()

    actual fun decrementAndGet(): Int = atomicInt.decrementAndGet()
}
