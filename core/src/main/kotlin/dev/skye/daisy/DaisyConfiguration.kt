package dev.skye.daisy

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

public data class DaisyQueue(
    val queueUrl: String,
    val waitTime: Duration,
    val batchSize: Int
)

public data class DaisyProcessingConfiguration(
    val quantity: Int,
    val dispatcher: CoroutineDispatcher
)

public data class DaisyPenaltiesConfiguration(
    val receivePenalty: Duration,
    val exceptionPenalty: Duration
)

public data class DaisyAWSConfiguration(
    val client: SqsAsyncClient
)

public data class DaisyMetricsConfiguration(
    val registry: MeterRegistry
)

public data class DaisyRoutingConfiguration(
    val router: MessageRouting
)

public data class DaisyConfiguration(
    public val queues: List<DaisyQueue>,
    public val processing: DaisyProcessingConfiguration = DaisyProcessingConfiguration(
        quantity = Runtime.getRuntime().availableProcessors(),
        dispatcher = Dispatchers.IO
    ),
    public val routing: DaisyRoutingConfiguration,
    public val penalties: DaisyPenaltiesConfiguration = DaisyPenaltiesConfiguration(
        receivePenalty = Duration.ofSeconds(10),
        exceptionPenalty = Duration.ofSeconds(10)
    ),
    public val aws: DaisyAWSConfiguration,
    public val metrics: DaisyMetricsConfiguration
)
