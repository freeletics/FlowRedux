package com.freeletics.flowredux.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

actual fun suspendTest(body: suspend CoroutineScope.() -> Unit) = runBlocking { body() }