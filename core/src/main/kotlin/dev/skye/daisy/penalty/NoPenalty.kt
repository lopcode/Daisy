package dev.skye.daisy.penalty

internal object NoPenalty : PenaltyStrategy {

    override suspend fun applyPenalty() {
    }

    override fun reset() {
    }
}
