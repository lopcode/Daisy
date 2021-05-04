package dev.skye.daisy.penalty

import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

// Maximum supported duration is 1 hour
internal class BackoffDelayPenalty(
    maxDuration: Duration,
    step: Duration = Duration.ofMillis(1000),
    private val delayer: suspend (Long) -> Unit = { durationMs -> delay(durationMs) }
) : PenaltyStrategy {

    private val counter = AtomicInteger(0)
    private val maxCounter = 22
    private val maxDurationMs = maxDuration
        .coerceAtMost(Duration.ofHours(1))
        .toMillis()
    private val stepMs = step.toMillis()

    override suspend fun applyAndIncrement() {
        val counter = counter.get().coerceAtMost(maxCounter)
        val multiplier = 2.toDouble().pow(counter)
            .coerceAtLeast(1.0)
            .toLong()
        val computedDurationMs = stepMs * multiplier
        val durationMs = computedDurationMs.coerceAtMost(maxDurationMs)

        increaseCounter()
        delayer(durationMs)
    }

    private fun increaseCounter() {
        val counterValue = counter.incrementAndGet()
        if (counterValue > maxCounter) {
            counter.set(maxCounter)
        }
    }

    override fun reset() {
        counter.set(0)
    }
}
