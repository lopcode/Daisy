package dev.skye.daisy.penalty

import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

// Maximum supported duration is 1 hour
internal class BackoffDelayPenalty(
    maxDuration: Duration,
    step: Duration = Duration.ofMillis(100),
    private val delayer: suspend (Long) -> Unit = { durationMs -> delay(durationMs) }
) : PenaltyStrategy {

    private val counter = AtomicInteger(0)
    private val maxDurationMs = maxDuration
        .coerceAtMost(Duration.ofHours(1))
        .toMillis()
    private val stepMs = step.toMillis()

    override suspend fun applyPenalty() {
        val multiplier = 2.toDouble().pow(counter.get())
            .coerceIn(1.0, Long.MAX_VALUE.toDouble())
            .toLong()
        val computedDurationMs = stepMs * multiplier
        val durationMs = java.lang.Long.min(computedDurationMs, maxDurationMs)

        increaseCounter()
        delayer(durationMs)
    }

    private fun increaseCounter() {
        val counterValue = counter.incrementAndGet()
        // If the counter runs forever, eventually it will wrap around
        // In that case, reset the penalty
        if (counterValue < 0) {
            reset()
        }
    }

    override fun reset() {
        counter.set(0)
    }
}
