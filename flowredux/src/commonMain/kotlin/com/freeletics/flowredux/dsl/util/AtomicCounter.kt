/*
 * Copyright 2022 LifeTime GmbH. All rights reserved.
 */

package com.freeletics.flowredux.dsl.util

public expect class AtomicCounter(initialValue: Int) {
    public fun get(): Int
    public fun set(newValue: Int)
    public fun incrementAndGet(): Int
    public fun decrementAndGet(): Int
}

public var AtomicCounter.value: Int
    get() = get()
    set(value) {
        set(value)
    }
