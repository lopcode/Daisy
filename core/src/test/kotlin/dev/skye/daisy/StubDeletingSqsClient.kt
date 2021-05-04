package dev.skye.daisy

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class StubDeletingSqsClient(
    messages: List<Message>,
    private val removeWhenSeen: Boolean = false
) : SqsAsyncClient {

    private val receiptHandles = ConcurrentHashMap.newKeySet<String>().apply {
        this.addAll(messages.map { it.receiptHandle() })
    }
    private val messages = messages.associateBy { it.receiptHandle() }

    override fun close() {
    }

    override fun receiveMessage(
        receiveMessageRequest: ReceiveMessageRequest
    ): CompletableFuture<ReceiveMessageResponse> {
        val randomMessageKey = receiptHandles.randomOrNull()
        val message = randomMessageKey?.let {
            messages[it]!!
        }
        if (removeWhenSeen) {
            receiptHandles.remove(randomMessageKey)
        }
        val messages = message?.let {
            listOf(it)
        } ?: listOf()
        val response = ReceiveMessageResponse.builder()
            .messages(messages)
            .build()
        return CompletableFuture.completedFuture(response)
    }

    override fun deleteMessage(
        deleteMessageRequest: DeleteMessageRequest
    ): CompletableFuture<DeleteMessageResponse> {
        val response = DeleteMessageResponse.builder()
            .build()
        receiptHandles.remove(deleteMessageRequest.receiptHandle())
        return CompletableFuture.completedFuture(response)
    }

    override fun serviceName(): String {
        return "daisy-sqs"
    }
}
