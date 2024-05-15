package com.freeletics.flowredux.util

internal actual class AtomicCounter actual constructor(initialValue: Int) {

    private var atomicInt = initialValue

    actual fun get(): Int = atomicInt
    actual fun incrementAndGet(): Int = ++atomicInt
    actual fun decrementAndGet(): Int = --atomicInt
}
