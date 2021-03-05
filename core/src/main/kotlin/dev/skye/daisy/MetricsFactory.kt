package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

internal fun MeterRegistry.makePolledCounter(queue: String): Counter {
    return this.counter(
        "messages.polled",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.makeGeneratedCounter(queue: String): Counter {
    return this.counter(
        "messages.generated",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.makeProcessedQueueCounter(queue: String): Counter {
    return this.counter(
        "messages.processed",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.makeProcessedTotalCounter(): Counter {
    return this.counter(
        "messages.processed.total"
    )
}

internal fun MeterRegistry.makeDeletedCounter(queue: String): Counter {
    return this.counter(
        "messages.deleted",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.makeFailedCounter(queue: String): Counter {
    return this.counter(
        "messages.failed",
        listOf(
            Tag.of("queue", queue)
        )
    )
}
