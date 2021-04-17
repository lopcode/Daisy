package dev.skye.daisy

import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import java.time.Duration

object Demo {

    @Serializable
    data class MessageBody(
        val message: String
    )

    private val registry = LoggingMeterRegistry(
        object : LoggingRegistryConfig {
            override fun step(): Duration {
                return Duration.ofSeconds(1)
            }

            override fun get(key: String): String? = null
        },
        Clock.SYSTEM
    )

    private val logger = logger<Demo>()
    private val client = InfiniteSQSAsyncClient(makeDemoMessage())

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val demoMessageProcessor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                val messageBody = Json.decodeFromString<MessageBody>(message.body())
                logger.debug("message ${message.messageId()}: $messageBody")
                return PostProcessAction.Delete
            }
        }

        val mainQueue = DaisyQueue(
            queueUrl = "https://test.local/0000/queue-1",
            waitTime = Duration.ofSeconds(20),
            batchSize = 10
        )
        val dlqQueue = DaisyQueue(
            queueUrl = "https://test.local/0000/queue-1-dlq",
            waitTime = Duration.ofSeconds(20),
            batchSize = 10
        )
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val mainQueues = generateSequence { mainQueue }.take(availableProcessors).toList()
        val dlqQueues = generateSequence { dlqQueue }.take(availableProcessors).toList()
        val configuration = DaisyConfiguration(
            queues = mainQueues + dlqQueues,
            penalties = DaisyPenaltiesConfiguration(
                receivePenalty = Duration.ofSeconds(5),
                exceptionPenalty = Duration.ofSeconds(5)
            ),
            aws = DaisyAWSConfiguration(
                client = client
            ),
            metrics = DaisyMetricsConfiguration(
                registry = registry
            ),
            routing = DaisyRoutingConfiguration(
                router = TypeAttributeRouter(
                    processors = mapOf(
                        "message_body_type" to demoMessageProcessor
                    )
                )
            ),
            processing = DaisyProcessingConfiguration(
                quantity = availableProcessors * 4,
                dispatcher = Dispatchers.IO
            )
        )
        val daisy = Daisy(configuration)

        logger.info("Starting - will continue indefinitely or until messages processed goes to zero...")
        val job = daisy.run()

        terminateAfter(
            supervisorJob = job,
            processedCounter = registry.counter("messages.processed.total")
        )
    }

    private fun makeDemoMessage(): Message {
        val messageBodyAttribute = MessageAttributeValue.builder()
            .dataType("String")
            .stringValue("message_body_type")
            .build()
        val messageBody = """
                { "message": "hello, world!" }
        """.trimIndent()
        val message = Message.builder()
            .messageAttributes(
                mapOf(
                    TypeAttributeRouter.DefaultMessageTypeAttributeName to messageBodyAttribute
                )
            )
            .body(messageBody)
            .build()
        return message
    }

    private suspend fun terminateAfter(
        threshold: Long = 0,
        minTimeMs: Long = 10_000L,
        supervisorJob: Job,
        processedCounter: Counter
    ) {
        val firstStart = System.currentTimeMillis()
        while (supervisorJob.isActive) {
            val processedMessages = processedCounter.count()
            val now = System.currentTimeMillis()
            if (processedMessages <= threshold && (now - firstStart > minTimeMs)) {
                logger.info("no more messages to process")
                supervisorJob.cancel()
                return
            }
            delay(1000)
        }
    }
}
