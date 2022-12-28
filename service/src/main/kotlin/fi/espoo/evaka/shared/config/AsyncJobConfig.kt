// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.config

import fi.espoo.evaka.EvakaEnv
import fi.espoo.evaka.shared.async.AsyncJob
import fi.espoo.evaka.shared.async.AsyncJobPool
import fi.espoo.evaka.shared.async.AsyncJobRunner
import fi.espoo.evaka.shared.async.SuomiFiAsyncJob
import fi.espoo.evaka.shared.async.UrgentAsyncJob
import fi.espoo.evaka.shared.async.VardaAsyncJob
import io.micrometer.core.instrument.MeterRegistry
import io.opentracing.Tracer
import java.time.Duration
import mu.KotlinLogging
import org.jdbi.v3.core.Jdbi
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class AsyncJobConfig {
    @Bean
    fun asyncJobRunner(jdbi: Jdbi, tracer: Tracer): AsyncJobRunner<AsyncJob> =
        AsyncJobRunner(AsyncJob::class, AsyncJobPool.Config(concurrency = 4), jdbi, tracer)

    @Bean
    fun urgentAsyncJobRunner(jdbi: Jdbi, tracer: Tracer): AsyncJobRunner<UrgentAsyncJob> =
        AsyncJobRunner(UrgentAsyncJob::class, AsyncJobPool.Config(concurrency = 4), jdbi, tracer)

    @Bean
    fun vardaAsyncJobRunner(jdbi: Jdbi, tracer: Tracer): AsyncJobRunner<VardaAsyncJob> =
        AsyncJobRunner(VardaAsyncJob::class, AsyncJobPool.Config(concurrency = 1), jdbi, tracer)

    @Bean
    fun sfiAsyncJobRunner(
        jdbi: Jdbi,
        tracer: Tracer,
        env: Environment
    ): AsyncJobRunner<SuomiFiAsyncJob> =
        AsyncJobRunner(
            SuomiFiAsyncJob::class,
            AsyncJobPool.Config(
                concurrency = 1,
                throttleInterval =
                    Duration.ofSeconds(1).takeIf { env.activeProfiles.contains("production") },
            ),
            jdbi,
            tracer
        )

    @Bean
    fun asyncJobRunnerStarter(
        asyncJobRunners: List<AsyncJobRunner<*>>,
        evakaEnv: EvakaEnv,
        meterRegistry: MeterRegistry
    ) =
        ApplicationListener<ApplicationReadyEvent> {
            val logger = KotlinLogging.logger {}
            if (evakaEnv.asyncJobRunnerDisabled) {
                logger.info("Async job runners disabled")
            } else {
                asyncJobRunners.forEach {
                    it.registerMeters(meterRegistry)
                    it.enableAfterCommitHook()
                    it.startBackgroundPolling()
                    logger.info("Async job runner ${it.name} started")
                }
            }
        }
}
