package dev.skye.daisy

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

data class DaisyQueue(
    val queueUrl: String,
    val waitTime: Duration,
    val batchSize: Int
)

data class DaisyProcessorsConfiguration(
    val quantity: Int,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
)

data class DaisyPenaltiesConfiguration(
    val receivePenalty: Duration,
    val exceptionPenalty: Duration
)

data class DaisyAWSConfiguration(
    val client: SqsAsyncClient
)

data class DaisyMetricsConfiguration(
    val registry: MeterRegistry
)

data class DaisyConfiguration(
    val queues: List<DaisyQueue>,
    val processors: DaisyProcessorsConfiguration = DaisyProcessorsConfiguration(
        quantity = Runtime.getRuntime().availableProcessors()
    ),
    val penalties: DaisyPenaltiesConfiguration = DaisyPenaltiesConfiguration(
        receivePenalty = Duration.ofSeconds(10),
        exceptionPenalty = Duration.ofSeconds(10)
    ),
    val aws: DaisyAWSConfiguration,
    val metrics: DaisyMetricsConfiguration
)
