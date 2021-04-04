package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.selectUnbiased
import software.amazon.awssdk.services.sqs.model.Message
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

public class Daisy(
    private val configuration: DaisyConfiguration
) {

    private data class ProducerSpec(
        val queueUrl: String,
        val poller: QueuePolling,
        val deleter: MessageDeleting,
        val delayer: MessageDelaying
    )

    private data class Work(
        val message: Message,
        val deleter: MessageDeleting,
        val delayer: MessageDelaying,
        val processedCounter: Counter,
        val failedCounter: Counter
    )

    private val logger = logger<Daisy>()
    private val registry = configuration.metrics.registry
    private val penalties = configuration.penalties
    private val router = configuration.routing.router

    public fun run(): Job {
        val producerSpecs = configuration.queues.map {
            val pollCounter = registry.makePolledCounter(it.queueUrl)
            val deleteCounter = registry.makeDeletedCounter(it.queueUrl)
            val delayCounter = registry.makeDelayedCounter(it.queueUrl)
            val poller = QueuePoller(
                queueUrl = it.queueUrl,
                waitTimeSeconds = it.waitTime.toSeconds().toInt(),
                batchSize = it.batchSize,
                client = configuration.aws.client,
                counter = pollCounter
            )
            val deleter = MessageDeleter(
                queueUrl = it.queueUrl,
                client = configuration.aws.client,
                counter = deleteCounter
            )
            val delayer = MessageDelayer(
                queueUrl = it.queueUrl,
                client = configuration.aws.client,
                counter = delayCounter
            )
            ProducerSpec(
                queueUrl = it.queueUrl,
                poller = poller,
                deleter = deleter,
                delayer = delayer
            )
        }

        val supervisorJob = SupervisorJob()
        val scope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = configuration.processing.dispatcher + supervisorJob
        }

        runCoroutines(scope, producerSpecs)

        return supervisorJob
    }

    private fun runCoroutines(
        scope: CoroutineScope,
        producerSpecs: List<ProducerSpec>
    ) {
        // producers
        val producers = producerSpecs.map {
            val processedCounter = registry.makeProcessedQueueCounter(it.queueUrl)
            val failedCounter = registry.makeFailedCounter(it.queueUrl)
            createProducer(scope, it.poller, it.deleter, it.delayer, processedCounter, failedCounter)
        }

        // sampler
        val primaryChannel = Channel<Work>()
        val samplerJob = scope.loopUntilCancelled(
            shouldYield = false,
            work = {
                randomSample(producers, primaryChannel)
            },
            onException = {
                logger.error("exception in sampler", it)
                scope.cancel(CancellationException(it))
            }
        )

        // processor pipelines
        val totalProcessedCounter = registry.makeProcessedTotalCounter()
        val processorJobs = scope.loopUntilCancelled(
            configuration.processing.quantity,
            shouldYield = true,
            work = {
                processWork(primaryChannel, totalProcessedCounter)
            },
            onException = {
                logger.error("exception logged in processor pipeline", it)
                delay(penalties.exceptionPenalty.toMillis())
            }
        )

        samplerJob.start()
        processorJobs
            .forEach { it.start() }
        producers
            .map { it.job }
            .forEach { it.start() }
    }

    private suspend fun processWork(
        primaryChannel: Channel<Work>,
        totalProcessedCounter: Counter
    ) {
        val work = primaryChannel.receive()
        logger.debug("routing message: ${work.message.messageId()}")

        val processor = router.route(work.message)
        if (processor == null) {
            logger.warn("no processor registered for message ${work.message.messageId()}")
            work.failedCounter.increment()
            return
        }

        when (val action = processor.process(work.message)) {
            is PostProcessAction.DoNothing -> Unit
            is PostProcessAction.Delete -> {
                val deleteResult = work.deleter.delete(
                    work.message.receiptHandle()
                )
                if (deleteResult is DeleteResult.Failure) {
                    logger.warn("message deletion failed", deleteResult.cause)
                }
            }
            is PostProcessAction.RetryLater -> {
                val delayResult = work.delayer.delay(
                    work.message.receiptHandle(),
                    duration = action.after
                )
                if (delayResult is DelayResult.Failure) {
                    logger.warn("message delay failed", delayResult.cause)
                }
            }
        }

        work.processedCounter.increment()
        totalProcessedCounter.increment()
    }

    private suspend fun randomSample(
        producers: List<Producer>,
        channel: Channel<Work>
    ) {
        val work = selectUnbiased<Work> {
            producers
                .map { it.channel }
                .map { channel ->
                    channel.onReceive { it }
                }
        }
        channel.send(work)
    }

    private data class Producer(
        val channel: Channel<Work>,
        val job: Job
    )
    private fun createProducer(
        scope: CoroutineScope,
        poller: QueuePolling,
        deleter: MessageDeleting,
        delayer: MessageDelaying,
        processedCounter: Counter,
        failedCounter: Counter
    ): Producer {
        val channel = Channel<Work>(poller.batchSize)
        val job = scope.loopUntilCancelled(
            shouldYield = true,
            work = {
                pollToChannel(poller, deleter, delayer, channel, processedCounter, failedCounter)
            },
            onException = {
                logger.error("error in message receiver", it)
                delay(penalties.exceptionPenalty.toMillis())
            }
        )
        return Producer(channel, job)
    }

    private suspend fun pollToChannel(
        poller: QueuePolling,
        deleter: MessageDeleting,
        delayer: MessageDelaying,
        channel: Channel<Work>,
        processedCounter: Counter,
        failedCounter: Counter
    ) {
        val messages = when (val pollResult = poller.poll()) {
            is PollResult.Success -> {
                logger.debug("fetched ${pollResult.messages.size} messages")
                pollResult.messages
            }

            is PollResult.Failure -> {
                logger.error("failed to poll", pollResult.cause)
                delay(penalties.receivePenalty.toMillis())
                return
            }
        }
        for (message in messages) {
            channel.send(Work(message, deleter, delayer, processedCounter, failedCounter))
        }
    }
}
