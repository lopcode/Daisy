package dev.skye.daisy.work

import software.amazon.awssdk.services.sqs.model.Message

internal data class Work(
    val queueUrl: String,
    val message: Message
)
