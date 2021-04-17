package dev.skye.daisy.processor

import dev.skye.daisy.action.PostProcessAction
import software.amazon.awssdk.services.sqs.model.Message

public interface MessageProcessing {

    public suspend fun process(message: Message): PostProcessAction
}
