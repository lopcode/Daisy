package dev.skye.daisy

import dev.skye.daisy.MessageGenerator.generateMessages
import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import java.net.URI
import java.time.Duration

object Demo {

    private val endpointUrl = "http://localhost:9324"
    private val queueUrl = "http://localhost:9324/000000000000/test-queue"
    private val dlqUrl = "http://localhost:9324/000000000000/test-dlq"
    private val credentials = AwsBasicCredentials.create("access key", "secret key")

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

    private val credentialsProvider = StaticCredentialsProvider.create(credentials)
    private val client = SqsAsyncClient.builder()
        .endpointOverride(URI.create(endpointUrl))
        .credentialsProvider(credentialsProvider)
        .build()
    private val logger = logger<Demo>()

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val demoMessageProcessor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                val messageBody = Json.decodeFromString<MessageBody>(message.body())
                logger.debug("message ${message.messageId()}: $messageBody")
                return PostProcessAction.Delete
            }
        }

        val configuration = DaisyConfiguration(
            queues = listOf(
                DaisyQueue(
                    queueUrl = queueUrl,
                    waitTime = Duration.ofSeconds(20),
                    batchSize = 10
                ),
                DaisyQueue(
                    queueUrl = dlqUrl,
                    waitTime = Duration.ofSeconds(20),
                    batchSize = 10
                )
            ),
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
                        MessageGenerator.messageBodyType to demoMessageProcessor
                    )
                )
            )
        )
        val daisy = Daisy(configuration)

        val seedMessageCount = 10_000
        generateMessages(seedMessageCount, configuration)
        logger.info("done")

        val job = daisy.run()

        terminateAfter(
            supervisorJob = job,
            processedCounter = registry.counter("messages.processed.total")
        )
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
