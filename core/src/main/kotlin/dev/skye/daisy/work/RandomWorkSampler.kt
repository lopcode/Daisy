package dev.skye.daisy.work

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.selectUnbiased

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
