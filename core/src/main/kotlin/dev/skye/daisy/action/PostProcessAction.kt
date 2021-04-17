package dev.skye.daisy.action

import java.time.Duration

public sealed class PostProcessAction {

    // Let SQS redrive policy determine what happens to the message
    public object DoNothing : PostProcessAction()

    // Change the SQS message visibility so that the message becomes visible at a later time
    public data class RetryLater(val after: Duration) : PostProcessAction()

    // Delete the message from the SQS queue
    public object Delete : PostProcessAction()
}
