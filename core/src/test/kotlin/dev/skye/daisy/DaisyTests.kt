package dev.skye.daisy

import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DaisyTests {

    private val messageCount = 100_000

    @Test fun `large number of messages are processed and acknowledged correctly`() = runBlocking {
        val receivedReceipts = ConcurrentHashMap.newKeySet<String>()
        val receiptCounter = AtomicInteger(0)
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                val receiptHandle = message.receiptHandle()
                if (receivedReceipts.contains(receiptHandle)) {
                    return PostProcessAction.DoNothing
                }
                receivedReceipts.add(receiptHandle)
                receiptCounter.incrementAndGet()
                return PostProcessAction.DoNothing
            }
        }
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val messageHandles = (1..messageCount).map {
            UUID.randomUUID().toString()
        }.toSet()
        val messages = messageHandles.map {
            Message.builder()
                .receiptHandle(it)
                .messageAttributes(
                    mapOf(
                        "test_type_attribute" to MessageAttributeValue
                            .builder()
                            .stringValue("test")
                            .build()
                    )
                )
                .build()
        }
        val sqsClient = StubbedSqsClient(messages)
        val configuration = DaisyConfiguration(
            queues = listOf(
                DaisyQueue(
                    queueUrl = "test",
                    waitTime = Duration.ofSeconds(20),
                    batchSize = 1
                )
            ),
            routing = DaisyRoutingConfiguration(
                router = router
            ),
            aws = DaisyAWSConfiguration(
                client = sqsClient
            ),
            metrics = DaisyMetricsConfiguration(
                registry = SimpleMeterRegistry()
            )
        )
        val daisy = Daisy(configuration)

        val job = daisy.run()

        val startTimeMs = System.currentTimeMillis()
        val startTime = Instant.ofEpochMilli(startTimeMs)
        val maxSeconds = 120
        while (true) {
            if (receivedReceipts.size >= messageCount) {
                assertEquals(receivedReceipts, messageHandles)

                job.cancel()
                job.join()

                return@runBlocking
            }

            if (Duration.between(Instant.now(), startTime).seconds > maxSeconds) {
                fail("failed to process $messageCount in $maxSeconds seconds - processed ${receivedReceipts.size}")
            }
            delay(100)
        }
    }
}
