package dev.skye.daisy

import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import dev.skye.daisy.utility.NoopMeterRegistry
import io.ktor.application.Application
import io.ktor.application.ApplicationStarted
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.fail

class DaisyFeatureTests {

    @Test
    fun `when installed, daisy processes a message after startup`() = runBlocking {
        val messageProcessed = AtomicBoolean(false)
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                messageProcessed.set(true)
                return PostProcessAction.DoNothing
            }
        }
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val message = Message.builder()
            .messageAttributes(
                mapOf(
                    "test_type_attribute" to MessageAttributeValue
                        .builder()
                        .stringValue("test")
                        .build()
                )
            )
            .build()
        val sqsClient = StubbedSqsClient(listOf(message))
        val configuration = DaisyConfiguration(
            queues = listOf(
                DaisyQueue(
                    url = "test",
                    waitDuration = Duration.ofSeconds(20),
                    batchSize = 1,
                    emptyPollPenalty = PenaltyConfiguration.NoPenalty
                )
            ),
            router = router,
            awsClient = sqsClient,
            meterRegistry = NoopMeterRegistry()
        )

        val startTimeMs = System.currentTimeMillis()
        val startTime = Instant.ofEpochMilli(startTimeMs)
        val maxSeconds = 30

        withTestApplication(moduleFunction = { testApp(configuration) }) {
            environment.monitor.raise(ApplicationStarted, this.application)

            while (!messageProcessed.get()) {
                if (Duration.between(Instant.now(), startTime).seconds > maxSeconds) {
                    fail("failed to process message in $maxSeconds seconds")
                }
                runBlocking {
                    delay(100)
                    yield()
                }
            }
        }
    }

    @Test
    fun `when installed with null configuration, ktor does not exit early`() = runBlocking {
        withTestApplication(moduleFunction = { testApp(null) }) {
            environment.monitor.raise(ApplicationStarted, this.application)

            val responseCode = handleRequest {
                uri = "/test"
            }.response.status()
            assertEquals(HttpStatusCode.NoContent, responseCode)
        }
    }
}

fun Application.testApp(configuration: DaisyConfiguration?) {
    install(DaisyFeature) {
        this.configuration = configuration
    }
    routing {
        get("/test") {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
