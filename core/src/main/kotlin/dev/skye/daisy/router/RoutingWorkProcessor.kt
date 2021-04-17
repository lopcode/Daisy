package dev.skye.daisy.router

import dev.skye.daisy.action.DelayResult
import dev.skye.daisy.action.DeleteResult
import dev.skye.daisy.action.MessageDelaying
import dev.skye.daisy.action.MessageDeleting
import dev.skye.daisy.action.PostProcessAction
import dev.skye.daisy.logger
import dev.skye.daisy.utility.failedCounter
import dev.skye.daisy.utility.processedCounter
import dev.skye.daisy.utility.processedTotalCounter
import dev.skye.daisy.work.Work
import dev.skye.daisy.work.WorkProcessing
import io.micrometer.core.instrument.MeterRegistry

internal class RoutingWorkProcessor(
    private val router: MessageRouting,
    private val deleter: MessageDeleting,
    private val delayer: MessageDelaying,
    private val meterRegistry: MeterRegistry
) : WorkProcessing {

    private val logger = logger<RoutingWorkProcessor>()

    override suspend fun process(work: Work) {
        logger.debug("routing message: ${work.message.messageId()}")

        val processor = router.route(work.message)
        if (processor == null) {
            logger.warn("no processor registered for message ${work.message.messageId()}")
            meterRegistry
                .failedCounter(work.queueUrl)
                .increment()
            return
        }

        when (val action = processor.process(work.message)) {
            is PostProcessAction.DoNothing -> Unit
            is PostProcessAction.Delete -> {
                val deleteResult = deleter.delete(
                    queueUrl = work.queueUrl,
                    receiptHandle = work.message.receiptHandle()
                )
                if (deleteResult is DeleteResult.Failure) {
                    logger.warn("message deletion failed", deleteResult.cause)
                }
            }
            is PostProcessAction.RetryLater -> {
                val delayResult = delayer.delay(
                    queueUrl = work.queueUrl,
                    receiptHandle = work.message.receiptHandle(),
                    duration = action.after
                )
                if (delayResult is DelayResult.Failure) {
                    logger.warn("message delay failed", delayResult.cause)
                }
            }
        }

        meterRegistry
            .processedCounter(work.queueUrl)
            .increment()
        meterRegistry
            .processedTotalCounter()
            .increment()
    }
}
