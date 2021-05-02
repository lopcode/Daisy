package dev.skye.daisy.poller

import dev.skye.daisy.logger
import dev.skye.daisy.utility.polledCounter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

internal class QueuePoller(
    override val batchSize: Int,
    override val queueUrl: String,
    private val waitTimeSeconds: Int,
    private val client: SqsAsyncClient,
    private val meterRegistry: MeterRegistry
) : QueuePolling {

    private val logger = logger<QueuePoller>()

    override suspend fun poll(): PollResult {
        val request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .attributeNamesWithStrings("All")
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
        meterRegistry
            .polledCounter(queueUrl)
            .increment(count)
        return PollResult.Success(response.messages())
    }
}
