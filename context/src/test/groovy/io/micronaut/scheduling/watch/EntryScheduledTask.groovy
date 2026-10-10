package io.micronaut.scheduling.watch

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled

import java.util.concurrent.CopyOnWriteArrayList

@EachProperty("scheduled-watch.jobs")
@Requires(property = "spec.name", value = "ScheduledMethodWatcherSpec-entries")
class EntryScheduledTask {
    static final List<String> RUNS = new CopyOnWriteArrayList<>()

    final String name
    boolean enabled

    EntryScheduledTask(@Parameter String name) {
        this.name = name
    }

    @Scheduled(fixedDelay = "1h")
    void run() {
        RUNS.add(name)
    }
}
