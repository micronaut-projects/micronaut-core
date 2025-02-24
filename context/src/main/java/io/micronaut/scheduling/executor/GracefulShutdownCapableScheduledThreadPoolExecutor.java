/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.scheduling.executor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.LongAdder;

/**
 * Extension of {@link java.util.concurrent.Executors#newScheduledThreadPool(int, ThreadFactory)}
 * that supports graceful shutdown.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
final class GracefulShutdownCapableScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor implements GracefulShutdownCapable {
    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();
    private final LongAdder running = new LongAdder();

    public GracefulShutdownCapableScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public @NonNull CompletionStage<?> shutdownGracefully() {
        shutdown();
        return terminationFuture;
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        running.increment();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        running.decrement();
    }

    @Override
    protected void terminated() {
        super.terminated();
        terminationFuture.complete(null);
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return OptionalLong.of(running.sum());
    }
}
