package dev.skye.daisy.penalty

import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class FixedDelayPenaltyTests {

    @Test
    fun `applyPenalty stays fixed`() = runBlocking {
        val expectedDelays = listOf<Long>(100, 100, 100)
        val recordedDelays = mutableListOf<Long>()
        val stubDelayer: suspend (Long) -> Unit = { delayMs ->
            recordedDelays += delayMs
        }
        val sut = FixedDelayPenalty(
            duration = Duration.ofMillis(100),
            delayer = stubDelayer
        )
        expectedDelays.forEach { _ ->
            sut.applyPenalty()
        }

        assertEquals(expectedDelays, recordedDelays)
    }

    @Test
    fun `reset has no effect`() = runBlocking {
        val expectedDelays = listOf<Long>(100, 100, 100)
        val recordedDelays = mutableListOf<Long>()
        val stubDelayer: suspend (Long) -> Unit = { delayMs ->
            recordedDelays += delayMs
        }
        val sut = FixedDelayPenalty(
            duration = Duration.ofMillis(100),
            delayer = stubDelayer
        )
        expectedDelays.forEachIndexed { index, _ ->
            if (index == 1) {
                sut.reset()
            }
            sut.applyPenalty()
        }

        assertEquals(expectedDelays, recordedDelays)
    }
}
