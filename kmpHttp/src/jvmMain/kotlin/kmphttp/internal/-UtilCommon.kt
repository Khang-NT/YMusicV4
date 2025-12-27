package kmphttp.internal

import kotlinx.coroutines.runInterruptible
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal suspend fun <T> runIoInterruptible(
    context: CoroutineContext = EmptyCoroutineContext,
    block: () -> T
): T {
    return runInterruptible(context) {
        try {
            block()
        } catch (e: InterruptedIOException) {
            // https://github.com/Kotlin/kotlinx.coroutines/issues/3551
            throw InterruptedException().initCause(e)
        }
    }

}