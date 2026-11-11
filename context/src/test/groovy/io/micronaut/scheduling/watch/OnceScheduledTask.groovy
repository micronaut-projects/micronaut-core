package io.micronaut.scheduling.watch

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(property = "spec.name", value = "ScheduledMethodWatcherSpec-entries")
class OnceScheduledTask {
    static final AtomicInteger RUNS = new AtomicInteger()

    @Scheduled(fixedDelay = "1h")
    void run() {
        RUNS.incrementAndGet()
    }
}
