package dev.skye.daisy

import dev.skye.daisy.penalty.PenaltyStrategy
import dev.skye.daisy.router.MessageRouting
import dev.skye.daisy.utility.NoopMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

public data class DaisyQueue(
    val url: String,
    val waitDuration: Duration,
    val batchSize: Int,
    val emptyPollPenalty: PenaltyConfiguration,
    val pollerCount: Int = 1
)

public data class DaisyProcessingConfiguration(
    val quantity: Int,
    val dispatcher: CoroutineDispatcher
)

public data class DaisyPenaltiesConfiguration(
    val pollException: PenaltyConfiguration,
    val processingException: PenaltyConfiguration
)

public sealed class PenaltyConfiguration {

    data class BackoffDelay(val maxDuration: Duration) : PenaltyConfiguration()
    data class FixedDelay(val duration: Duration) : PenaltyConfiguration()
    data class Custom(val strategy: PenaltyStrategy) : PenaltyConfiguration()
    object NoPenalty : PenaltyConfiguration()
}

public data class DaisyConfiguration(
    public val queues: List<DaisyQueue>,
    public val processing: DaisyProcessingConfiguration = DaisyProcessingConfiguration(
        quantity = Runtime.getRuntime().availableProcessors(),
        dispatcher = Dispatchers.IO
    ),
    public val penalties: DaisyPenaltiesConfiguration = DaisyPenaltiesConfiguration(
        pollException = PenaltyConfiguration.BackoffDelay(Duration.ofMinutes(1)),
        processingException = PenaltyConfiguration.BackoffDelay(Duration.ofMinutes(1))
    ),
    public val router: MessageRouting,
    public val awsClient: SqsAsyncClient,
    public val meterRegistry: MeterRegistry = NoopMeterRegistry()
)
