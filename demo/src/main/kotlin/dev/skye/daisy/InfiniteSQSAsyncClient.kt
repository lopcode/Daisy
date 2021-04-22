package dev.skye.daisy

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class InfiniteSQSAsyncClient(
    private val messageTemplate: Message,
    private val sleepDuration: Duration = Duration.ZERO,
    private val executorService: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
) : SqsAsyncClient {

    override fun close() {
    }

    override fun receiveMessage(
        receiveMessageRequest: ReceiveMessageRequest
    ): CompletableFuture<ReceiveMessageResponse> {
        return CompletableFuture.supplyAsync(
            {
                val batchSize = receiveMessageRequest.maxNumberOfMessages()
                val messages = generateSequence {
                    messageTemplate.toBuilder()
                        .receiptHandle(UUID.randomUUID().toString())
                        .messageId(UUID.randomUUID().toString())
                        .build()
                }.take(batchSize).toList()
                val response = ReceiveMessageResponse.builder()
                    .messages(messages)
                    .build()
                if (!sleepDuration.isZero) {
                    Thread.sleep(sleepDuration.toMillis())
                }
                return@supplyAsync response
            },
            executorService
        )
    }

    override fun deleteMessage(
        deleteMessageRequest: DeleteMessageRequest
    ): CompletableFuture<DeleteMessageResponse> {
        val response = DeleteMessageResponse.builder()
            .build()
        return CompletableFuture.completedFuture(response)
    }

    override fun serviceName(): String {
        return "daisy-sqs"
    }
}
