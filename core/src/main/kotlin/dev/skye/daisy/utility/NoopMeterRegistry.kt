package dev.skye.daisy.utility

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Measurement
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.pause.PauseDetector
import io.micrometer.core.instrument.noop.NoopCounter
import io.micrometer.core.instrument.noop.NoopDistributionSummary
import io.micrometer.core.instrument.noop.NoopFunctionCounter
import io.micrometer.core.instrument.noop.NoopFunctionTimer
import io.micrometer.core.instrument.noop.NoopGauge
import io.micrometer.core.instrument.noop.NoopMeter
import io.micrometer.core.instrument.noop.NoopTimer
import java.util.concurrent.TimeUnit
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction

public class NoopMeterRegistry : MeterRegistry(Clock.SYSTEM) {

    override fun <T : Any?> newGauge(
        id: Meter.Id,
        obj: T?,
        valueFunction: ToDoubleFunction<T>
    ): Gauge {
        return NoopGauge(id)
    }

    override fun newCounter(
        id: Meter.Id
    ): Counter {
        return NoopCounter(id)
    }

    override fun newTimer(
        id: Meter.Id,
        distributionStatisticConfig: DistributionStatisticConfig,
        pauseDetector: PauseDetector
    ): Timer {
        return NoopTimer(id)
    }

    override fun newDistributionSummary(
        id: Meter.Id,
        distributionStatisticConfig: DistributionStatisticConfig,
        scale: Double
    ): DistributionSummary {
        return NoopDistributionSummary(id)
    }

    override fun newMeter(
        id: Meter.Id,
        type: Meter.Type,
        measurements: MutableIterable<Measurement>
    ): Meter {
        return NoopMeter(id)
    }

    override fun <T : Any?> newFunctionTimer(
        id: Meter.Id,
        obj: T,
        countFunction: ToLongFunction<T>,
        totalTimeFunction: ToDoubleFunction<T>,
        totalTimeFunctionUnit: TimeUnit
    ): FunctionTimer {
        return NoopFunctionTimer(id)
    }

    override fun <T : Any?> newFunctionCounter(
        id: Meter.Id,
        obj: T,
        countFunction: ToDoubleFunction<T>
    ): FunctionCounter {
        return NoopFunctionCounter(id)
    }

    override fun getBaseTimeUnit(): TimeUnit {
        return TimeUnit.NANOSECONDS
    }

    override fun defaultHistogramConfig(): DistributionStatisticConfig {
        return DistributionStatisticConfig.NONE
    }
}
