package dev.skye.daisy.router

import dev.skye.daisy.processor.MessageProcessing
import software.amazon.awssdk.services.sqs.model.Message

public interface MessageRouting {

    public fun route(message: Message): MessageProcessing?
}
