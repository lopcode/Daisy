package dev.skye.daisy

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.selectUnbiased

internal interface WorkSampling {

    suspend fun sample(
        inputs: List<Channel<Work>>
    ): Work
}

internal class RandomWorkSampler : WorkSampling {

    override suspend fun sample(
        inputs: List<Channel<Work>>
    ): Work {
        return selectUnbiased {
            inputs.map { channel ->
                channel.onReceive { it }
            }
        }
    }
}
