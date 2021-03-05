package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

sealed class PollResult {
    data class Failure(val cause: Throwable) : PollResult()
    data class Success(val messages: List<Message>) : PollResult()
}

interface QueuePolling {

    val batchSize: Int
    suspend fun poll(): PollResult
}

class QueuePoller(
    override val batchSize: Int,
    private val queueUrl: String,
    private val waitTimeSeconds: Int,
    private val client: SqsAsyncClient,
    private val counter: Counter
) : QueuePolling {

    private val logger = logger<QueuePoller>()

    override suspend fun poll(): PollResult {
        val request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .attributeNamesWithStrings("MessageDeduplicationId", "MessageGroupId")
            .messageAttributeNames("*")
            .maxNumberOfMessages(batchSize)
            .waitTimeSeconds(waitTimeSeconds)
            .build()

        val response = try {
            client.receiveMessage(request).await()
        } catch (exception: SdkException) {
            return PollResult.Failure(exception)
        }

        counter.increment(response.messages().size.toDouble())
        return PollResult.Success(response.messages())
    }
}
