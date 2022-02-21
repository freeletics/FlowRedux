/*
 * Copyright 2022 LifeTime GmbH. All rights reserved.
 */

package com.freeletics.flowredux.dsl.util

public expect class AtomicInt(initialValue: Int) {
    public fun get(): Int
    public fun set(newValue: Int)
    public fun incrementAndGet(): Int
    public fun decrementAndGet(): Int

    public fun addAndGet(delta: Int): Int
    public fun compareAndSet(expected: Int, new: Int): Boolean
}

public var AtomicInt.value: Int
    get() = get()
    set(value) {
        set(value)
    }