/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling;

import io.micronaut.scheduling.exceptions.TaskExecutionException;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a {@link Runnable} and re-schedules the tasks.
 *
 * @param <V> The result type returned by this Future
 *
 * @author graemerocher
 * @since 1.0
 */
class ReschedulingTask<V> implements ScheduledFuture<V>, Runnable, Callable<V> {

    private final Callable<V> task;
    private final TaskScheduler taskScheduler;
    private final NextFireTime nextTime;
    private ScheduledFuture<?> currentFuture;
    private AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * @param task          The task
     * @param taskScheduler To schedule the task for next time
     * @param nextTime      The next time
     */
    ReschedulingTask(Callable<V> task, TaskScheduler taskScheduler, NextFireTime nextTime) {
        this.task = task;
        this.taskScheduler = taskScheduler;
        this.nextTime = nextTime;
        this.currentFuture = taskScheduler.schedule(nextTime.get(), (Callable<V>) this);
    }

    @Override
    public V call() throws Exception {
        try {
            return task.call();
        } finally {
            synchronized (this) {
                if (!cancelled.get()) {
                    this.currentFuture =
                        taskScheduler.schedule(nextTime.get(), (Callable<V>) this);
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            call();
        } catch (Exception e) {
            throw new TaskExecutionException("Error executing task: " + e.getMessage(), e);
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        ScheduledFuture current;
        synchronized (this) {
            current = this.currentFuture;
        }
        return current.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed o) {
        ScheduledFuture current;
        synchronized (this) {
            current = this.currentFuture;
        }

        return current.compareTo(o);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        ScheduledFuture current;
        synchronized (this) {
            cancelled.set(true);
            current = this.currentFuture;
        }
        return current.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isDone() {
        synchronized (this) {
            return this.currentFuture.isDone();
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        ScheduledFuture current;
        synchronized (this) {
            cancelled.set(true);
            current = this.currentFuture;
        }
        return (V) current.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        ScheduledFuture current;
        synchronized (this) {
            cancelled.set(true);
            current = this.currentFuture;
        }
        return (V) current.get(timeout, unit);
    }
}
