package dev.skye.daisy

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

internal sealed class PollResult {
    internal data class Failure(val cause: Throwable) : PollResult()
    internal data class Success(val messages: List<Message>) : PollResult()
}

internal interface QueuePolling {

    val batchSize: Int
    val queueUrl: String
    suspend fun poll(): PollResult
}

internal class QueuePoller(
    override val batchSize: Int,
    override val queueUrl: String,
    private val waitTimeSeconds: Int,
    private val client: SqsAsyncClient,
    private val registry: MeterRegistry
) : QueuePolling {

    private val logger = logger<QueuePoller>()

    override suspend fun poll(): PollResult {
        val request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .attributeNamesWithStrings("MessageDeduplicationId", "MessageGroupId")
            .messageAttributeNames(".*")
            .maxNumberOfMessages(batchSize)
            .waitTimeSeconds(waitTimeSeconds)
            .build()

        val response = try {
            client.receiveMessage(request).await()
        } catch (exception: SdkException) {
            return PollResult.Failure(exception)
        }

        val count = response.messages().size.toDouble()
        registry
            .polledCounter(queueUrl)
            .increment(count)
        return PollResult.Success(response.messages())
    }
}
