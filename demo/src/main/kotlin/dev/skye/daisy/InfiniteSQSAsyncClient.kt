package dev.skye.daisy

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InfiniteSQSAsyncClient(
    private val messageTemplate: Message,
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
                        .build()
                }.take(batchSize).toList()
                val response = ReceiveMessageResponse.builder()
                    .messages(messages)
                    .build()
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
