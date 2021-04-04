package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import java.time.Duration

internal sealed class DelayResult {
    data class Failure(val cause: Throwable) : DelayResult()
    object Success : DelayResult()
}

internal interface MessageDelaying {

    suspend fun delay(
        receiptHandle: String,
        duration: Duration
    ): DelayResult
}

internal class MessageDelayer(
    private val queueUrl: String,
    private val client: SqsAsyncClient,
    private val counter: Counter
) : MessageDelaying {

    private val logger = logger<QueuePoller>()

    // Note that at the time of writing, SQS supports a maximum message visibility of 12 hours
    override suspend fun delay(
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

        counter.increment()
        return DelayResult.Success
    }
}
