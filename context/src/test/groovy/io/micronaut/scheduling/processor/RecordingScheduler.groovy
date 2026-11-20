package io.micronaut.scheduling.processor

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Records the timers and tasks scheduled on it, and runs a hook once from within a schedule call.
 */
@Singleton
@Named("recording")
@Requires(property = "spec.name", value = "ScheduledMethodProcessorSpec")
class RecordingScheduler implements TaskScheduler {
    final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>()
    final List<Runnable> tasks = new CopyOnWriteArrayList<>()
    final AtomicReference<Runnable> onSchedule = new AtomicReference<>()
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()

    private ScheduledFuture<?> record(Runnable task, ScheduledFuture<?> future) {
        tasks.add(task)
        futures.add(future)
        Runnable hook = onSchedule.getAndSet(null)
        if (hook != null) {
            hook.run()
        }
        return future
    }

    @Override
    ScheduledFuture<?> schedule(String cron, Runnable command) {
        throw new UnsupportedOperationException()
    }

    @Override
    <V> ScheduledFuture<V> schedule(String cron, Callable<V> command) {
        throw new UnsupportedOperationException()
    }

    @Override
    ScheduledFuture<?> schedule(Duration delay, Runnable command) {
        record(command, executor.schedule(command, delay.toMillis(), TimeUnit.MILLISECONDS))
    }

    @Override
    <V> ScheduledFuture<V> schedule(Duration delay, Callable<V> callable) {
        throw new UnsupportedOperationException()
    }

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable command) {
        record(command, executor.scheduleAtFixedRate(command, initialDelay == null ? 0 : initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS))
    }

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable command) {
        record(command, executor.scheduleWithFixedDelay(command, initialDelay == null ? 0 : initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS))
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow()
    }
}
