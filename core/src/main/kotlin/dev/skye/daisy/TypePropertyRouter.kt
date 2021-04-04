package dev.skye.daisy

import software.amazon.awssdk.services.sqs.model.Message

public class TypePropertyRouter(
    private val messageAttributeName: String = DefaultMessageTypeAttributeName,
    private val processors: Map<String, MessageProcessing>
) : MessageRouting {

    private val logger = logger<TypePropertyRouter>()

    public companion object {
        const val DefaultMessageTypeAttributeName = "daisy_message_type"
    }

    public override fun route(message: Message): MessageProcessing? {
        val type = message.messageAttributes()[messageAttributeName]?.stringValue()
        if (type == null) {
            logger.warn("message missing expected attribute \"$messageAttributeName\" ${message.messageId()}")
            return null
        }

        val processor = processors[type]
        if (processor == null) {
            logger.warn("no processor registered for message type \"$type\"")
            return null
        }

        return processor
    }
}
