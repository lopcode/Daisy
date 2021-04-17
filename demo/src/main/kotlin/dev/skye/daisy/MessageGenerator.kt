package dev.skye.daisy

import dev.skye.daisy.router.TypeAttributeRouter.Companion.DefaultMessageTypeAttributeName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import java.util.UUID

object MessageGenerator {

    private val logger = logger<MessageGenerator>()
    const val messageBodyType = "message_body_type"

    fun generateMessages(
        messageCount: Int,
        configuration: DaisyConfiguration
    ) = runBlocking {
        logger.info("generating $messageCount messages...")
        val parallelism = 10
        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        val jobs = (0..parallelism).map {
            scope.launch(Dispatchers.IO) {
                val step = 10
                configuration.queues.forEach { queue ->
                    for (i in 1..(messageCount / step / parallelism)) {
                        val entries = makeMessages()
                            .take(step)
                            .toList()
                        val request = SendMessageBatchRequest.builder()
                            .queueUrl(queue.queueUrl)
                            .entries(entries)
                            .build()

                        val response = try {
                            configuration.aws.client.sendMessageBatch(request).await()
                        } catch (exception: Exception) {
                            logger.error("failed to generate message", exception)
                            return@forEach
                        } as SendMessageBatchResponse
                        if (response.hasFailed()) {
                            logger.error("failed to send message batch", response.failed())
                            return@forEach
                        }
                        configuration
                            .metrics
                            .registry
                            .makeGeneratedCounter(queue.queueUrl)
                            .increment(step.toDouble())
                    }
                }
            }
        }
        jobs.joinAll()
    }

    private fun makeMessages(): Sequence<SendMessageBatchRequestEntry> {
        val requests = generateSequence {
            val messageBodyAttribute = MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("message_body_type")
                .build()
            val messageBody = """
                { "message": "hello, world!" }
            """.trimIndent()
            SendMessageBatchRequestEntry.builder()
                .id(UUID.randomUUID().toString())
                .delaySeconds(0)
                .messageAttributes(
                    mapOf(
                        DefaultMessageTypeAttributeName to messageBodyAttribute
                    )
                )
                .messageBody(messageBody)
                .build()
        }
        return requests
    }
}
