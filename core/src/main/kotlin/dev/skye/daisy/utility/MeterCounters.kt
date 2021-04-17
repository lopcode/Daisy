package dev.skye.daisy.utility

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

internal fun MeterRegistry.polledCounter(queue: String): Counter {
    return this.counter(
        "messages.polled",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.processedCounter(queue: String): Counter {
    return this.counter(
        "messages.processed",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.processedTotalCounter(): Counter {
    return this.counter(
        "messages.processed.total"
    )
}

internal fun MeterRegistry.deletedCounter(queue: String): Counter {
    return this.counter(
        "messages.deleted",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.delayedCounter(queue: String): Counter {
    return this.counter(
        "messages.delayed",
        listOf(
            Tag.of("queue", queue)
        )
    )
}

internal fun MeterRegistry.failedCounter(queue: String): Counter {
    return this.counter(
        "messages.failed",
        listOf(
            Tag.of("queue", queue)
        )
    )
}
