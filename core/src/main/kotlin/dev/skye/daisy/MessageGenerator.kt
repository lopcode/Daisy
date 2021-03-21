package dev.skye.daisy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.util.UUID

object MessageGenerator {

    private val logger = logger<MessageGenerator>()

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

                        configuration.aws.client.sendMessageBatch(request).await()
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
            SendMessageBatchRequestEntry.builder()
                .id(UUID.randomUUID().toString())
                .delaySeconds(0)
                .messageBody("hello, world")
                .build()
        }
        return requests
    }
}
