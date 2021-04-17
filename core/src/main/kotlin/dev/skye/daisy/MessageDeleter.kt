package dev.skye.daisy

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest

internal sealed class DeleteResult {

    internal data class Failure(val cause: Throwable) : DeleteResult()
    internal object Success : DeleteResult()
}

internal interface MessageDeleting {

    suspend fun delete(
        queueUrl: String,
        receiptHandle: String
    ): DeleteResult
}

internal class MessageDeleter(
    private val client: SqsAsyncClient,
    private val meterRegistry: MeterRegistry
) : MessageDeleting {

    private val logger = logger<QueuePoller>()

    override suspend fun delete(
        queueUrl: String,
        receiptHandle: String
    ): DeleteResult {
        val request = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build()

        try {
            client.deleteMessage(request).await()
        } catch (exception: SdkException) {
            DeleteResult.Failure(exception)
        }

        meterRegistry
            .deletedCounter(queueUrl)
            .increment()
        return DeleteResult.Success
    }
}
