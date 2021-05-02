package dev.skye.daisy

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.concurrent.CompletableFuture

class StubThrowingSqsClient : SqsAsyncClient {

    override fun close() {
    }

    override fun receiveMessage(
        receiveMessageRequest: ReceiveMessageRequest
    ): CompletableFuture<ReceiveMessageResponse> {
        throw RuntimeException("intentional failure")
    }

    override fun serviceName(): String {
        return "daisy-sqs"
    }
}
