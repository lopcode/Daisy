package dev.skye.daisy

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

internal fun MeterRegistry.makeGeneratedCounter(queue: String): Counter {
    return this.counter(
        "messages.generated",
        listOf(
            Tag.of("queue", queue)
        )
    )
}
