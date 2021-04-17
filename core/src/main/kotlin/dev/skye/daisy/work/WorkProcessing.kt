package dev.skye.daisy.work

internal interface WorkProcessing {

    suspend fun process(work: Work)
}
