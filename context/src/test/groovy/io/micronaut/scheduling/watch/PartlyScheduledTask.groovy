package io.micronaut.scheduling.watch

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(property = "spec.name", value = "ScheduledMethodWatcherSpec-partial")
class PartlyScheduledTask {
    final AtomicInteger runs = new AtomicInteger()

    @Scheduled(fixedRate = "30ms")
    @Scheduled(fixedRate = '${partly.rate:40ms}')
    void tick() {
        runs.incrementAndGet()
    }
}
