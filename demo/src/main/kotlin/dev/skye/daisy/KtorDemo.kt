package dev.skye.daisy

import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import java.time.Duration

private val logger = logger<DaisyFeature>()

@Serializable
internal data class MessageBody(
    val message: String
)

private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

internal fun main() {
    val client = InfiniteSQSAsyncClient(
        messageTemplate = makeDemoMessage(),
        sleepDuration = Duration.ofMillis(1000)
    )
    val demoMessageProcessor = object : MessageProcessing {
        override suspend fun process(message: Message): PostProcessAction {
            val messageBody = Json.decodeFromString<MessageBody>(message.body())
            logger.info("received message ${message.messageId()}: $messageBody")
            return PostProcessAction.Delete
        }
    }
    val mainQueue = DaisyQueue(
        url = "https://test.local/0000/queue-1",
        waitDuration = Duration.ofSeconds(20),
        batchSize = 1,
        emptyPollPenalty = PenaltyConfiguration.NoPenalty
    )
    val dlqQueue = DaisyQueue(
        url = "https://test.local/0000/queue-1-dlq",
        waitDuration = Duration.ofSeconds(20),
        batchSize = 1,
        emptyPollPenalty = PenaltyConfiguration.NoPenalty
    )
    val configuration = DaisyConfiguration(
        queues = listOf(mainQueue, dlqQueue),
        awsClient = client,
        meterRegistry = registry,
        router = TypeAttributeRouter(
            processors = mapOf(
                "message_body_type" to demoMessageProcessor
            )
        ),
        processing = DaisyProcessingConfiguration(
            quantity = 1,
            dispatcher = Dispatchers.IO
        )
    )

    embeddedServer(CIO, port = 8080) {
        install(MicrometerMetrics) {
            registry = dev.skye.daisy.registry
        }
        install(DaisyFeature) {
            this.configuration = configuration
        }
        routing {
            get("/metrics") {
                call.respond(registry.scrape())
            }
        }
    }.start(wait = true)
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
