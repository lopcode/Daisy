package dev.skye.daisy

internal interface WorkProcessing {

    suspend fun process(work: Work)
}