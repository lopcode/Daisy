package dev.skye.daisy.action

import java.time.Duration

internal sealed class DelayResult {

    data class Failure(val cause: Throwable) : DelayResult()
    object Success : DelayResult()
}

internal interface MessageDelaying {

    suspend fun delay(
        queueUrl: String,
        receiptHandle: String,
        duration: Duration
    ): DelayResult
}
