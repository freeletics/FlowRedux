@file:Suppress("unused") // Called by platform code.

package com.freeletics.flowredux2.sample

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Suppress("FunctionName")
fun ImmediateMainScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
