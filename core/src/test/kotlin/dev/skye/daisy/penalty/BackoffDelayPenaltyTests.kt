package dev.skye.daisy.penalty

import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class BackoffDelayPenaltyTests {

    @Test
    fun `applyPenalty exponentially increases delay time`() = runBlocking {
        val expectedDelays = listOf<Long>(100, 200, 400, 800, 1600, 2000)
        val recordedDelays = mutableListOf<Long>()
        val stubDelayer: suspend (Long) -> Unit = { delayMs ->
            recordedDelays += delayMs
        }
        val sut = BackoffDelayPenalty(
            maxDuration = Duration.ofSeconds(2),
            step = Duration.ofMillis(100),
            delayer = stubDelayer
        )
        expectedDelays.forEach { _ ->
            sut.applyAndIncrement()
        }

        assertEquals(expectedDelays, recordedDelays)
    }

    @Test
    fun `reset reduces delay to first value`() = runBlocking {
        val expectedDelays = listOf<Long>(100, 200, 400, 100, 200, 400)
        val recordedDelays = mutableListOf<Long>()
        val stubDelayer: suspend (Long) -> Unit = { delayMs ->
            recordedDelays += delayMs
        }
        val sut = BackoffDelayPenalty(
            maxDuration = Duration.ofSeconds(2),
            step = Duration.ofMillis(100),
            delayer = stubDelayer
        )
        expectedDelays.forEachIndexed { index, _ ->
            if (index == 3) {
                sut.reset()
            }
            sut.applyAndIncrement()
        }

        assertEquals(expectedDelays, recordedDelays)
    }
}
