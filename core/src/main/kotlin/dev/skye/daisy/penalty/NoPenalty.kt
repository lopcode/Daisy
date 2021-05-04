package dev.skye.daisy.penalty

internal object NoPenalty : PenaltyStrategy {

    override suspend fun applyAndIncrement() {
    }

    override fun reset() {
    }
}
