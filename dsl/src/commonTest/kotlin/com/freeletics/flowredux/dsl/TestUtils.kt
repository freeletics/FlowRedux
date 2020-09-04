package com.freeletics.flowredux.dsl

import kotlinx.coroutines.CoroutineScope

expect fun suspendTest(body: suspend CoroutineScope.() -> Unit)