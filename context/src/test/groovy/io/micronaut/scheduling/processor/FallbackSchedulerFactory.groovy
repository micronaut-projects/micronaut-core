package io.micronaut.scheduling.processor

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * A scheduler of the name that a nullable factory does not produce, and an executor of the same name.
 */
@Factory
@Requires(property = "spec.name", value = "ScheduledMethodProcessorSpec")
class FallbackSchedulerFactory {

    @Singleton
    @Named("fallback")
    @Nullable
    TaskScheduler absentScheduler() {
        return null
    }

    @Singleton
    @Named("fallback")
    @Bean(preDestroy = "shutdownNow")
    ScheduledExecutorService fallbackExecutor() {
        return Executors.newSingleThreadScheduledExecutor()
    }
}
