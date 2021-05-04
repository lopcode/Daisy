package dev.skye.daisy.penalty

import kotlinx.coroutines.delay
import java.time.Duration

internal class FixedDelayPenalty(
    private val duration: Duration,
    private val delayer: suspend (Long) -> Unit = { durationMs -> delay(durationMs) }
) : PenaltyStrategy {

    override suspend fun applyAndIncrement() {
        if (duration == Duration.ZERO) {
            return
        }

        delayer(duration.toMillis())
    }

    override fun reset() {
    }
}
