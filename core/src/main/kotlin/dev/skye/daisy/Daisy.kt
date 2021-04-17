package dev.skye.daisy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

public class Daisy(
    private val configuration: DaisyConfiguration
) {

    private data class ProducerSpec(
        val queueUrl: String,
        val poller: QueuePolling
    )

    private val logger = logger<Daisy>()
    private val registry = configuration.metrics.registry
    private val penalties = configuration.penalties
    private val router = configuration.routing.router
    private val sampler = RandomWorkSampler()

    public fun run(): Job {
        val deleter = MessageDeleter(
            client = configuration.aws.client,
            meterRegistry = registry
        )
        val delayer = MessageDelayer(
            client = configuration.aws.client,
            meterRegistry = registry
        )

        val producerSpecs = configuration.queues.map {
            val poller = QueuePoller(
                queueUrl = it.queueUrl,
                waitTimeSeconds = it.waitTime.toSeconds().toInt(),
                batchSize = it.batchSize,
                client = configuration.aws.client,
                registry = registry
            )
            ProducerSpec(
                queueUrl = it.queueUrl,
                poller = poller
            )
        }

        val supervisorJob = SupervisorJob()
        val scope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = configuration.processing.dispatcher + supervisorJob
        }

        runCoroutines(
            scope,
            producerSpecs,
            deleter = deleter,
            delayer = delayer
        )

        return supervisorJob
    }

    private fun runCoroutines(
        scope: CoroutineScope,
        producerSpecs: List<ProducerSpec>,
        deleter: MessageDeleting,
        delayer: MessageDelaying
    ) {
        // producers
        val producers = producerSpecs.map {
            createProducer(scope, it.poller)
        }

        // sampler
        val primaryChannel = Channel<Work>()
        val samplerJob = scope.loopUntilCancelled(
            shouldYield = false,
            work = {
                val work = sampler.sample(
                    inputs = producers.map { it.channel }
                )
                primaryChannel.send(work)
            },
            onException = {
                logger.error("exception in sampler", it)
                scope.cancel(CancellationException(it))
            }
        )

        // processor pipelines
        val processorJobs = scope.loopUntilCancelled(
            configuration.processing.quantity,
            shouldYield = true,
            work = {
                processWork(
                    primaryChannel,
                    delayer = delayer,
                    deleter = deleter
                )
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
        deleter: MessageDeleting,
        delayer: MessageDelaying
    ) {
        val work = primaryChannel.receive()
        logger.debug("routing message: ${work.message.messageId()}")

        val processor = router.route(work.message)
        if (processor == null) {
            logger.warn("no processor registered for message ${work.message.messageId()}")
            registry
                .failedCounter(work.queueUrl)
                .increment()
            return
        }

        when (val action = processor.process(work.message)) {
            is PostProcessAction.DoNothing -> Unit
            is PostProcessAction.Delete -> {
                val deleteResult = deleter.delete(
                    queueUrl = work.queueUrl,
                    receiptHandle = work.message.receiptHandle()
                )
                if (deleteResult is DeleteResult.Failure) {
                    logger.warn("message deletion failed", deleteResult.cause)
                }
            }
            is PostProcessAction.RetryLater -> {
                val delayResult = delayer.delay(
                    queueUrl = work.queueUrl,
                    receiptHandle = work.message.receiptHandle(),
                    duration = action.after
                )
                if (delayResult is DelayResult.Failure) {
                    logger.warn("message delay failed", delayResult.cause)
                }
            }
        }

        registry
            .processedCounter(work.queueUrl)
            .increment()
        registry
            .processedTotalCounter()
            .increment()
    }

    private data class Producer(
        val channel: Channel<Work>,
        val job: Job
    )
    private fun createProducer(
        scope: CoroutineScope,
        poller: QueuePolling
    ): Producer {
        val channel = Channel<Work>(poller.batchSize)
        val job = scope.loopUntilCancelled(
            shouldYield = true,
            work = {
                pollToChannel(poller, channel)
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
        channel: Channel<Work>
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
            val work = Work(
                queueUrl = poller.queueUrl,
                message = message
            )
            channel.send(work)
        }
    }
}
