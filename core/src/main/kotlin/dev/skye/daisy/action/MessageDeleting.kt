package dev.skye.daisy.action

internal sealed class DeleteResult {

    internal data class Failure(val cause: Throwable) : DeleteResult()
    internal object Success : DeleteResult()
}

internal interface MessageDeleting {

    suspend fun delete(
        queueUrl: String,
        receiptHandle: String
    ): DeleteResult
}
