import kotlinx.coroutines.CoroutineScope

expect fun suspendTest(body: suspend CoroutineScope.() -> Unit)