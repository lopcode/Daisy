package dev.skye.daisy

import dev.skye.daisy.action.MessageDelayer
import dev.skye.daisy.action.MessageDeleter
import dev.skye.daisy.penalty.PenaltyStrategy
import dev.skye.daisy.penalty.makePenalty
import dev.skye.daisy.poller.PollResult
import dev.skye.daisy.poller.QueuePoller
import dev.skye.daisy.poller.QueuePolling
import dev.skye.daisy.router.RoutingWorkProcessor
import dev.skye.daisy.utility.loopUntilCancelled
import dev.skye.daisy.work.RandomWorkSampler
import dev.skye.daisy.work.Work
import dev.skye.daisy.work.WorkProcessing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

public class Daisy(
    private val configuration: DaisyConfiguration
) {

    private data class ProducerSpec(
        val queueUrl: String,
        val poller: QueuePolling,
        val emptyPollPenalty: PenaltyStrategy
    )

    private val logger = logger<Daisy>()
    private val meterRegistry = configuration.meterRegistry
    private val penalties = configuration.penalties
    private val router = configuration.router
    private val awsClient = configuration.awsClient
    private val sampler = RandomWorkSampler()

    public fun run(): Job {
        val deleter = MessageDeleter(
            client = awsClient,
            meterRegistry = meterRegistry
        )
        val delayer = MessageDelayer(
            client = awsClient,
            meterRegistry = meterRegistry
        )
        val processor = RoutingWorkProcessor(
            router = router,
            deleter = deleter,
            delayer = delayer,
            meterRegistry = meterRegistry
        )

        val producerSpecs = configuration.queues
            .map { queueSpec ->
                val pollerCount = queueSpec.pollerCount.coerceAtLeast(1)
                (0..pollerCount).map {
                    val poller = QueuePoller(
                        queueUrl = queueSpec.url,
                        waitTimeSeconds = queueSpec.waitDuration.toSeconds().toInt(),
                        batchSize = queueSpec.batchSize,
                        client = awsClient,
                        meterRegistry = meterRegistry
                    )
                    val emptyPollPenalty = queueSpec.emptyPollPenalty.makePenalty()
                    ProducerSpec(
                        queueUrl = queueSpec.url,
                        poller = poller,
                        emptyPollPenalty = emptyPollPenalty,
                    )
                }
            }
            .flatten()

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
            createProducer(scope, it)
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
            exceptionPenaltyConfiguration = penalties.processingException,
            shouldYield = true,
            work = {
                val work = workChannel.receive()
                processor.process(work)
            },
            onException = {
                logger.error("exception logged in processor pipeline", it)
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
        spec: ProducerSpec
    ): Producer {
        val channel = Channel<Work>(spec.poller.batchSize)
        val job = scope.loopUntilCancelled(
            exceptionPenaltyConfiguration = penalties.pollException,
            shouldYield = true,
            work = {
                pollToChannel(spec, channel)
            },
            onException = {
                logger.error("error in message receiver", it)
            }
        )
        return Producer(channel, job)
    }

    private suspend fun pollToChannel(
        spec: ProducerSpec,
        channel: Channel<Work>
    ) {
        val messages = when (val pollResult = spec.poller.poll()) {
            is PollResult.Success -> {
                logger.debug("fetched ${pollResult.messages.size} messages")
                pollResult.messages
            }

            is PollResult.Failure -> {
                throw pollResult.cause
            }
        }

        if (messages.isEmpty()) {
            spec.emptyPollPenalty.applyPenalty()
        } else {
            spec.emptyPollPenalty.reset()
        }

        for (message in messages) {
            val work = Work(
                queueUrl = spec.poller.queueUrl,
                message = message
            )
            channel.send(work)
        }
    }
}
