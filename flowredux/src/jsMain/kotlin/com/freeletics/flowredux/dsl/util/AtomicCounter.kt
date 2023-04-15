package com.freeletics.flowredux.dsl.util

internal actual class AtomicCounter actual constructor(initialValue: Int) {

    private var atomicInt = initialValue

    public actual fun get(): Int = atomicInt
    public actual fun incrementAndGet(): Int = ++atomicInt
    public actual fun decrementAndGet(): Int = --atomicInt
}
