package dev.skye.daisy

import software.amazon.awssdk.services.sqs.model.Message

public interface MessageRouting {

    public fun route(message: Message): MessageProcessing?
}
