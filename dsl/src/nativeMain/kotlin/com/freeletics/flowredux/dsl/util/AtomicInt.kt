package com.freeletics.flowredux.dsl.util

import kotlin.native.concurrent.AtomicInt

public actual class AtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInt(initialValue)

    public actual fun get(): Int = atom.value

    public actual fun set(newValue: Int) {
        atom.value = newValue
    }

    public actual fun incrementAndGet(): Int = atom.addAndGet(1)

    public actual fun decrementAndGet(): Int = atom.addAndGet(-1)

    public actual fun addAndGet(delta: Int): Int = atom.addAndGet(delta)

    public actual fun compareAndSet(expected: Int, new: Int): Boolean = atom.compareAndSet(expected, new)
}