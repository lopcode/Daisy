package dev.skye.daisy.poller

import software.amazon.awssdk.services.sqs.model.Message

internal sealed class PollResult {
    internal data class Failure(val cause: Throwable) : PollResult()
    internal data class Success(val messages: List<Message>) : PollResult()
}

internal interface QueuePolling {

    val batchSize: Int
    val queueUrl: String
    suspend fun poll(): PollResult
}
