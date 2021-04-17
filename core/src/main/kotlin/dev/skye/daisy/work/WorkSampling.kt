package dev.skye.daisy.work

import kotlinx.coroutines.channels.Channel

internal interface WorkSampling {

    suspend fun sample(
        inputs: List<Channel<Work>>
    ): Work
}
