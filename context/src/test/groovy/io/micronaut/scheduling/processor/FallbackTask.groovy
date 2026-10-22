package io.micronaut.scheduling.processor

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(property = "spec.name", value = "ScheduledMethodProcessorSpec")
class FallbackTask {
    final AtomicInteger runs = new AtomicInteger()

    @Scheduled(fixedRate = "20ms", scheduler = "fallback")
    void tick() {
        runs.incrementAndGet()
    }
}
