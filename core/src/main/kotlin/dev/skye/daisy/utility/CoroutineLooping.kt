package dev.skye.daisy.utility

import dev.skye.daisy.PenaltyConfiguration
import dev.skye.daisy.logger
import dev.skye.daisy.penalty.makePenalty
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
    exceptionPenaltyConfiguration: PenaltyConfiguration = PenaltyConfiguration.NoPenalty,
    shouldYield: Boolean,
    work: suspend () -> Unit,
    onException: suspend (Throwable) -> Unit
): List<Job> {
    return (0..count).map {
        this.loopUntilCancelled(
            start = start,
            exceptionPenaltyConfiguration = exceptionPenaltyConfiguration,
            shouldYield = shouldYield,
            work = work,
            onException = onException
        )
    }
}

internal fun CoroutineScope.loopUntilCancelled(
    start: CoroutineStart = CoroutineStart.LAZY,
    exceptionPenaltyConfiguration: PenaltyConfiguration = PenaltyConfiguration.NoPenalty,
    shouldYield: Boolean,
    work: suspend () -> Unit,
    onException: suspend (Throwable) -> Unit
): Job {
    return this.launch(
        this.coroutineContext,
        start = start
    ) {
        val failurePenalty = exceptionPenaltyConfiguration.makePenalty()
        while (isActive) {
            try {
                work()
                failurePenalty.reset()
            } catch (exception: CancellationException) {
                // no-op
                logger<CoroutineScope>().debug("loop exiting due to cancellation", exception)
                return@launch
            } catch (exception: Exception) {
                onException(exception)
                failurePenalty.applyPenalty()
            }
            if (shouldYield) {
                yield()
            }
        }
    }
}
