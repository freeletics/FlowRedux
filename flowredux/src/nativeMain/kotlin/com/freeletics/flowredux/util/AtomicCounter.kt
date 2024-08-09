package com.freeletics.flowredux.util

import kotlin.concurrent.AtomicInt

internal actual class AtomicCounter actual constructor(initialValue: Int) {
    private val atomicInt = AtomicInt(initialValue)

    actual fun get(): Int = atomicInt.value

    actual fun incrementAndGet(): Int = atomicInt.addAndGet(1)

    actual fun decrementAndGet(): Int = atomicInt.addAndGet(-1)
}
