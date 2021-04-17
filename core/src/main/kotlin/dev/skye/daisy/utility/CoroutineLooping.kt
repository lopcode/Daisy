package dev.skye.daisy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal fun CoroutineScope.loopUntilCancelled(
    count: Int = 1,
    start: CoroutineStart = CoroutineStart.LAZY,
    shouldYield: Boolean,
    work: suspend () -> Unit,
    onException: suspend (Throwable) -> Unit
): List<Job> {
    return (0..count).map {
        this.loopUntilCancelled(start, shouldYield, work, onException)
    }
}

internal fun CoroutineScope.loopUntilCancelled(
    start: CoroutineStart = CoroutineStart.LAZY,
    shouldYield: Boolean,
    work: suspend () -> Unit,
    onException: suspend (Throwable) -> Unit
): Job {
    return this.launch(
        this.coroutineContext,
        start = start
    ) {
        while (isActive) {
            try {
                work()
            } catch (exception: CancellationException) {
                // no-op
                logger<CoroutineScope>().debug("loop exiting due to cancellation", exception)
                return@launch
            } catch (exception: Exception) {
                onException(exception)
            }
            if (shouldYield) {
                yield()
            }
        }
    }
}
