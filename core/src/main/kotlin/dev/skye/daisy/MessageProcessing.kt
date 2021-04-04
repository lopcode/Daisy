package dev.skye.daisy

import software.amazon.awssdk.services.sqs.model.Message

public interface MessageProcessing {

    public suspend fun process(message: Message): PostProcessAction
}
