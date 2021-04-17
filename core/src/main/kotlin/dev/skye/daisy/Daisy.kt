package dev.skye.daisy

import dev.skye.daisy.action.MessageDelayer
import dev.skye.daisy.action.MessageDeleter
import dev.skye.daisy.poller.PollResult
import dev.skye.daisy.poller.QueuePoller
import dev.skye.daisy.poller.QueuePolling
import dev.skye.daisy.router.RoutingWorkProcessor
import dev.skye.daisy.work.RandomWorkSampler
import dev.skye.daisy.work.Work
import dev.skye.daisy.work.WorkProcessing
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
    private val meterRegistry = configuration.metrics.registry
    private val penalties = configuration.penalties
    private val router = configuration.routing.router
    private val sampler = RandomWorkSampler()

    public fun run(): Job {
        val deleter = MessageDeleter(
            client = configuration.aws.client,
            meterRegistry = meterRegistry
        )
        val delayer = MessageDelayer(
            client = configuration.aws.client,
            meterRegistry = meterRegistry
        )
        val processor = RoutingWorkProcessor(
            router = router,
            deleter = deleter,
            delayer = delayer,
            meterRegistry = meterRegistry
        )

        val producerSpecs = configuration.queues.map {
            val poller = QueuePoller(
                queueUrl = it.queueUrl,
                waitTimeSeconds = it.waitTime.toSeconds().toInt(),
                batchSize = it.batchSize,
                client = configuration.aws.client,
                meterRegistry = meterRegistry
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
            processor
        )

        return supervisorJob
    }

    private fun runCoroutines(
        scope: CoroutineScope,
        producerSpecs: List<ProducerSpec>,
        processor: WorkProcessing
    ) {
        // producers
        val producers = producerSpecs.map {
            createProducer(scope, it.poller)
        }

        // samplers
        val workChannel = Channel<Work>()
        val samplerJobs = scope.loopUntilCancelled(
            count = 1,
            shouldYield = false,
            work = {
                val work = sampler.sample(
                    inputs = producers.map { it.channel }
                )
                workChannel.send(work)
            },
            onException = {
                logger.error("exception in sampler", it)
                scope.cancel(CancellationException(it))
            }
        )

        // processor pipelines
        val processorJobs = scope.loopUntilCancelled(
            count = configuration.processing.quantity,
            shouldYield = true,
            work = {
                val work = workChannel.receive()
                processor.process(work)
            },
            onException = {
                logger.error("exception logged in processor pipeline", it)
                delay(penalties.exceptionPenalty.toMillis())
            }
        )

        samplerJobs
            .forEach { it.start() }
        processorJobs
            .forEach { it.start() }
        producers
            .map { it.job }
            .forEach { it.start() }
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
