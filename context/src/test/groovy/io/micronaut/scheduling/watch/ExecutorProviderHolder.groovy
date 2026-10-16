package io.micronaut.scheduling.watch

import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.concurrent.ExecutorService

@Singleton
@Requires(property = "spec.name", value = "ScheduledMethodWatcherSpec")
class ExecutorProviderHolder {
    final BeanProvider<ExecutorService> executor

    ExecutorProviderHolder(@Named(TaskExecutors.SCHEDULED) BeanProvider<ExecutorService> executor) {
        this.executor = executor
    }
}
