package dev.skye.daisy

import io.ktor.application.Application
import io.ktor.application.ApplicationFeature
import io.ktor.application.ApplicationStarted
import io.ktor.application.ApplicationStopPreparing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

public class DaisyFeature {

    public class Configuration(
        var configuration: DaisyConfiguration?
    )
    public companion object Feature : ApplicationFeature<Application, Configuration, DaisyFeature> {

        public override val key = AttributeKey<DaisyFeature>("DaisyFeature")

        public override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit
        ): DaisyFeature {
            val appliedConfiguration = Configuration(null).apply(configure)
            val daisyConfiguration = appliedConfiguration.configuration
            if (daisyConfiguration == null) {
                pipeline.environment.log.error("Daisy has not been configured, and so will not start")
                return DaisyFeature()
            }

            val feature = DaisyFeature()
            val daisy = Daisy(daisyConfiguration)
            var parentJob: Job? = null

            pipeline.environment.monitor.subscribe(ApplicationStarted) {
                it.environment.log.info("Daisy starting...")
                parentJob = daisy.run()
            }

            pipeline.environment.monitor.subscribe(ApplicationStopPreparing) {
                val job = parentJob
                if (job == null) {
                    it.log.debug("Daisy was not configured, so nothing to do to stop it")
                    return@subscribe
                }

                it.log.info("Daisy stopping...")
                job.cancel(CancellationException("application shutting down"))
                runBlocking {
                    job.join()
                }
            }

            return feature
        }
    }
}
