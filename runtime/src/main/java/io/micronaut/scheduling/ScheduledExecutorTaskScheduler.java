/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling;

import static io.micronaut.core.util.ArgumentUtils.check;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.cron.CronExpression;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple abstraction over {@link ScheduledExecutorService}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Named(TaskExecutors.SCHEDULED)
@Primary
@Singleton
public class ScheduledExecutorTaskScheduler implements TaskScheduler {

    private final ScheduledExecutorService executorService;

    /**
     * @param executorService To schedule executor tasks
     */
    public ScheduledExecutorTaskScheduler(@Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        if (!(executorService instanceof ScheduledExecutorService)) {
            throw new IllegalStateException("Cannot schedule tasks on ExecutorService that is not a ScheduledExecutorService: " + executorService);
        }
        this.executorService = (ScheduledExecutorService) executorService;
    }

    @Override
    public ScheduledFuture<?> schedule(String cron, Runnable command) {
        if (StringUtils.isEmpty(cron)) {
            throw new IllegalArgumentException("Blank cron expression not allowed");
        }
        check("command", command).notNull();

        NextFireTime delaySupplier = new NextFireTime(CronExpression.create(cron));
        return new ReschedulingTask<>(() -> {
            command.run();
            return null;
        }, this, delaySupplier);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(String cron, Callable<V> command) {
        if (StringUtils.isEmpty(cron)) {
            throw new IllegalArgumentException("Blank cron expression not allowed");
        }
        check("command", command).notNull();

        NextFireTime delaySupplier = new NextFireTime(CronExpression.create(cron));
        return new ReschedulingTask<>(command, this, delaySupplier);
    }

    @Override
    public ScheduledFuture<?> schedule(Duration delay, Runnable command) {
        check("delay", delay).notNull();
        check("command", command).notNull();

        return executorService.schedule(
            command,
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Duration delay, Callable<V> callable) {
        check("delay", delay).notNull();
        check("callable", callable).notNull();
        return executorService.schedule(
            callable,
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable command) {
        check("period", period).notNull();
        check("command", command).notNull();
        long initialDelayMillis = initialDelay != null ? initialDelay.toMillis() : 0;
        return executorService.scheduleAtFixedRate(
            command,
            initialDelayMillis,
            period.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable command) {
        check("delay", delay).notNull();
        check("command", command).notNull();
        long initialDelayMillis = initialDelay != null ? initialDelay.toMillis() : 0;
        return executorService.scheduleWithFixedDelay(
            command,
            initialDelayMillis,
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

}
