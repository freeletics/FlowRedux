package com.freeletics.flowredux

import kotlinx.coroutines.CoroutineScope

expect fun suspendTest(body: suspend CoroutineScope.() -> Unit)