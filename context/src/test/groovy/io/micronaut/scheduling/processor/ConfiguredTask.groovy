package io.micronaut.scheduling.processor

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(property = "spec.name", value = "ScheduledMethodProcessorSpec")
class ConfiguredTask {
    final AtomicInteger runs = new AtomicInteger()

    @Scheduled(fixedRate = '${processor-spec.rate:1h}', initialDelay = "1h", scheduler = "recording")
    void tick() {
        runs.incrementAndGet()
    }
}
