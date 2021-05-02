package dev.skye.daisy

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

class StubHighThroughputSqsClient(
    messages: List<Message>
) : SqsAsyncClient {

    private val messages = ConcurrentLinkedQueue(messages)

    override fun close() {
    }

    override fun receiveMessage(
        receiveMessageRequest: ReceiveMessageRequest
    ): CompletableFuture<ReceiveMessageResponse> {
        val messages = messages.poll()?.let {
            listOf(it)
        } ?: listOf()
        val response = ReceiveMessageResponse.builder()
            .messages(messages)
            .build()
        return CompletableFuture.completedFuture(response)
    }

    override fun serviceName(): String {
        return "daisy-sqs"
    }
}
