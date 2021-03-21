package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest

sealed class DeleteResult {
    data class Failure(val cause: Throwable) : DeleteResult()
    object Success : DeleteResult()
}

interface MessageDeleting {

    suspend fun delete(receiptHandle: String): DeleteResult
}

class MessageDeleter(
    private val queueUrl: String,
    private val client: SqsAsyncClient,
    private val counter: Counter
) : MessageDeleting {

    private val logger = logger<QueuePoller>()

    override suspend fun delete(receiptHandle: String): DeleteResult {
        val request = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build()

        try {
            client.deleteMessage(request).await()
        } catch (exception: SdkException) {
            DeleteResult.Failure(exception)
        }

        counter.increment()
        return DeleteResult.Success
    }
}