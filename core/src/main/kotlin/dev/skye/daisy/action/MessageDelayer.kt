package dev.skye.daisy.action

import dev.skye.daisy.logger
import dev.skye.daisy.poller.QueuePoller
import dev.skye.daisy.utility.delayedCounter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import java.time.Duration

internal class MessageDelayer(
    private val client: SqsAsyncClient,
    private val meterRegistry: MeterRegistry
) : MessageDelaying {

    private val logger = logger<QueuePoller>()

    // Note that at the time of writing, SQS supports a maximum message visibility of 12 hours
    override suspend fun delay(
        queueUrl: String,
        receiptHandle: String,
        duration: Duration
    ): DelayResult {
        val timeoutSeconds = duration.toSeconds()
            .coerceIn(0, Int.MAX_VALUE.toLong())
            .toInt()
        val request = ChangeMessageVisibilityRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(timeoutSeconds)
            .build()

        try {
            client.changeMessageVisibility(request).await()
        } catch (exception: SdkException) {
            DelayResult.Failure(exception)
        }

        meterRegistry
            .delayedCounter(queueUrl)
            .increment()
        return DelayResult.Success
    }
}
