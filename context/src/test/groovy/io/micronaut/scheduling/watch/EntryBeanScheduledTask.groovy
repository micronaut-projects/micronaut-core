package io.micronaut.scheduling.watch

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled

import java.util.concurrent.CopyOnWriteArrayList

@EachBean(EntryScheduledTask)
@Requires(property = "spec.name", value = "ScheduledMethodWatcherSpec-entries")
class EntryBeanScheduledTask {
    static final List<String> RUNS = new CopyOnWriteArrayList<>()

    final EntryScheduledTask task

    EntryBeanScheduledTask(EntryScheduledTask task) {
        this.task = task
    }

    @Scheduled(fixedDelay = "1h")
    void run() {
        RUNS.add(task.name)
    }
}
