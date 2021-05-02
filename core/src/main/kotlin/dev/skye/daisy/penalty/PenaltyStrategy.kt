package dev.skye.daisy.penalty

import dev.skye.daisy.PenaltyConfiguration

public interface PenaltyStrategy {

    suspend fun applyPenalty()
    fun reset()
}

internal fun PenaltyConfiguration.makePenalty(): PenaltyStrategy {
    return when (this) {
        is PenaltyConfiguration.NoPenalty -> NoPenalty
        is PenaltyConfiguration.FixedDelay -> FixedDelayPenalty(this.duration)
        is PenaltyConfiguration.BackoffDelay -> BackoffDelayPenalty(this.maxDuration)
        is PenaltyConfiguration.Custom -> this.strategy
    }
}
