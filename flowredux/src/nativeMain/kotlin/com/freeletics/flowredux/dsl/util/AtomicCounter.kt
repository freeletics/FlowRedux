package com.freeletics.flowredux.dsl.util

import kotlin.native.concurrent.AtomicInt

internal actual class AtomicCounter actual constructor(initialValue: Int) {

    private val atomicInt = AtomicInt(initialValue)

    public actual fun get(): Int = atomicInt.value
    public actual fun incrementAndGet(): Int = atomicInt.addAndGet(1)
    public actual fun decrementAndGet(): Int = atomicInt.addAndGet(-1)
}
