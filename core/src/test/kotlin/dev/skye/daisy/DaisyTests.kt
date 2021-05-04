package dev.skye.daisy

import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.penalty.PenaltyStrategy
import dev.skye.daisy.processor.MessageProcessing
import dev.skye.daisy.router.TypeAttributeRouter
import dev.skye.daisy.utility.NoopMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DaisyTests {

    private var job: Job? = null

    @AfterTest
    fun tearDown() = runBlocking {
        job?.apply {
            this.cancel()
            this.join()
        }
        Unit
    }

    @Test
    fun `single message is processed once`() = runBlocking {
        val receivedReceipts = ConcurrentHashMap.newKeySet<String>()
        val receiptCounter = AtomicInteger(0)
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                val receiptHandle = message.receiptHandle()
                if (receivedReceipts.contains(receiptHandle)) {
                    fail("message already ackowledged $receiptHandle")
                }
                receivedReceipts.add(receiptHandle)
                receiptCounter.incrementAndGet()
                return PostProcessAction.Delete
            }
        }
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val messageHandle = UUID.randomUUID().toString()
        val messages = makeMessage(messageHandle)
        val sqsClient = StubDeletingSqsClient(
            listOf(messages),
            removeWhenSeen = true
        )
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            processing = DaisyProcessingConfiguration(
                quantity = 1,
                dispatcher = Dispatchers.IO
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        val startTimeMs = System.currentTimeMillis()
        val startTime = Instant.ofEpochMilli(startTimeMs)
        val maxSeconds = 5

        while (true) {
            if (receivedReceipts.size >= 1) {
                assertEquals(receivedReceipts, setOf(messageHandle))

                return@runBlocking
            }

            if (Duration.between(Instant.now(), startTime).seconds > maxSeconds) {
                fail("failed to process message in $maxSeconds seconds - processed ${receivedReceipts.size}")
            }
            delay(100)
        }
    }

    @Test
    fun `large number of messages are processed and acknowledged correctly`() = runBlocking {
        val messageCount = 100_000
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
            makeMessage(it)
        }
        val sqsClient = StubHighThroughputSqsClient(messages)
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            processing = DaisyProcessingConfiguration(
                quantity = Runtime.getRuntime().availableProcessors(),
                dispatcher = Dispatchers.IO
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        val startTimeMs = System.currentTimeMillis()
        val startTime = Instant.ofEpochMilli(startTimeMs)
        val maxSeconds = 120
        while (true) {
            if (receivedReceipts.size >= messageCount) {
                assertEquals(receivedReceipts, messageHandles)

                return@runBlocking
            }

            if (Duration.between(Instant.now(), startTime).seconds > maxSeconds) {
                fail("failed to process $messageCount in $maxSeconds seconds - processed ${receivedReceipts.size}")
            }
            delay(100)
        }
    }

    @Test
    fun `messages still processed even with unreliable processor`() = runBlocking {
        val messageCount = 100
        val receivedReceipts = ConcurrentHashMap.newKeySet<String>()
        val firstReceiveReceipts = ConcurrentHashMap.newKeySet<String>()
        val receiptCounter = AtomicInteger(0)
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                val receiptHandle = message.receiptHandle()
                if (!firstReceiveReceipts.contains(receiptHandle)) {
                    firstReceiveReceipts.add(receiptHandle)
                    throw RuntimeException("intentional failure")
                }
                if (receivedReceipts.contains(receiptHandle)) {
                    return PostProcessAction.DoNothing
                }
                receivedReceipts.add(receiptHandle)
                receiptCounter.incrementAndGet()
                return PostProcessAction.Delete
            }
        }
        val messageHandles = (1..messageCount).map {
            UUID.randomUUID().toString()
        }.toSet()
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val messages = messageHandles.map {
            makeMessage(it)
        }
        val sqsClient = StubDeletingSqsClient(messages)
        val configuration = makeTestConfiguration(router, sqsClient)
        val daisy = Daisy(configuration)

        job = daisy.run()

        val startTimeMs = System.currentTimeMillis()
        val startTime = Instant.ofEpochMilli(startTimeMs)
        val maxSeconds = 120
        while (true) {
            if (receivedReceipts.size >= messageCount) {
                assertEquals(receivedReceipts, messageHandles)

                return@runBlocking
            }

            if (Duration.between(Instant.now(), startTime).seconds > maxSeconds) {
                fail("failed to process $messageCount in $maxSeconds seconds - processed ${receivedReceipts.size}")
            }
            delay(100)
        }
    }

    @Test
    fun `processor exception results in application of penalty`() = runBlocking {
        val expectedThrows = 3
        val thrownCounter = AtomicInteger(0)
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                if (thrownCounter.get() < expectedThrows) {
                    thrownCounter.incrementAndGet()
                    throw RuntimeException("intentional failure")
                }

                job?.cancel()
                delay(1000)
                return PostProcessAction.Delete
            }
        }
        val processorPenalty = mock<PenaltyStrategy>()
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val sqsClient = StubDeletingSqsClient(
            messages = listOf(
                makeMessage(UUID.randomUUID().toString())
            )
        )
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            penalties = DaisyPenaltiesConfiguration(
                pollException = PenaltyConfiguration.NoPenalty,
                processingException = PenaltyConfiguration.Custom(processorPenalty)
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        verify(
            processorPenalty,
            timeout(2000).times(expectedThrows)
        ).applyAndIncrement()
    }

    @Test
    fun `polling exception results in application of penalty`() = runBlocking {
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                job?.cancel()
                delay(1000)
                return PostProcessAction.Delete
            }
        }
        val pollPenalty = mock<PenaltyStrategy>()
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val sqsClient = StubThrowingSqsClient()
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            penalties = DaisyPenaltiesConfiguration(
                pollException = PenaltyConfiguration.Custom(pollPenalty),
                processingException = PenaltyConfiguration.NoPenalty
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        verify(
            pollPenalty,
            timeout(2000).atLeastOnce()
        ).applyAndIncrement()
    }

    @Test
    fun `empty poll results in application of penalty`() = runBlocking {
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                job?.cancel()
                delay(1000)
                return PostProcessAction.Delete
            }
        }
        val emptyPenalty = mock<PenaltyStrategy>()
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val sqsClient = StubDeletingSqsClient(
            messages = listOf()
        )
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            queues = listOf(
                DaisyQueue(
                    url = "test",
                    waitDuration = Duration.ofSeconds(20),
                    batchSize = 1,
                    emptyPollPenalty = PenaltyConfiguration.Custom(emptyPenalty)
                )
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        verify(
            emptyPenalty,
            timeout(2000).atLeastOnce()
        ).applyAndIncrement()
    }

    @Test
    fun `non empty poll results in penalty reset`() = runBlocking {
        val processor = object : MessageProcessing {
            override suspend fun process(message: Message): PostProcessAction {
                job?.cancel()
                delay(1000)
                return PostProcessAction.Delete
            }
        }
        val emptyPenalty = mock<PenaltyStrategy>()
        val router = TypeAttributeRouter(
            messageAttributeName = "test_type_attribute",
            processors = mapOf(
                "test" to processor
            )
        )
        val sqsClient = StubDeletingSqsClient(
            messages = listOf(
                makeMessage(UUID.randomUUID().toString())
            )
        )
        val configuration = makeTestConfiguration(
            router,
            sqsClient,
            queues = listOf(
                DaisyQueue(
                    url = "test",
                    waitDuration = Duration.ofSeconds(20),
                    batchSize = 1,
                    emptyPollPenalty = PenaltyConfiguration.Custom(emptyPenalty)
                )
            )
        )
        val daisy = Daisy(configuration)

        job = daisy.run()

        verify(
            emptyPenalty,
            timeout(2000).atLeastOnce()
        ).reset()
    }

    private fun makeTestConfiguration(
        router: TypeAttributeRouter,
        sqsClient: SqsAsyncClient,
        queues: List<DaisyQueue> = listOf(
            DaisyQueue(
                url = "test",
                waitDuration = Duration.ofSeconds(20),
                batchSize = 1,
                emptyPollPenalty = PenaltyConfiguration.NoPenalty
            )
        ),
        processing: DaisyProcessingConfiguration = DaisyProcessingConfiguration(
            quantity = 1,
            dispatcher = Dispatchers.IO
        ),
        penalties: DaisyPenaltiesConfiguration = DaisyPenaltiesConfiguration(
            pollException = PenaltyConfiguration.NoPenalty,
            processingException = PenaltyConfiguration.NoPenalty
        )
    ) = DaisyConfiguration(
        queues = queues,
        penalties = penalties,
        processing = processing,
        router = router,
        awsClient = sqsClient,
        meterRegistry = NoopMeterRegistry()
    )

    private fun makeMessage(receiptHandle: String) = Message.builder()
        .receiptHandle(receiptHandle)
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
