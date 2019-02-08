/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.scheduling.instrument;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * An {@link ExecutorService} that has been instrumented to allow for propagation of thread state
 * and other instrumentation related tasks.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InstrumentedExecutorService extends ExecutorService, InstrumentedExecutor {

    /**
     * Implementors can override to specify the target {@link ExecutorService}.
     *
     * @return The target {@link ExecutorService}
     */
    @Override
    ExecutorService getTarget();

    /**
     * Instruments the given callable task.
     *
     * @param task the task to instrument
     * @param <T> The generic return type
     * @return The callable
     */
    default <T> Callable<T> instrument(Callable<T> task) {
        return task;
    }

    @Override
    default void shutdown() {
        getTarget().shutdown();
    }

    @Override
    default List<Runnable> shutdownNow() {
        return getTarget().shutdownNow();
    }

    @Override
    default boolean isShutdown() {
        return getTarget().isShutdown();
    }

    @Override
    default boolean isTerminated() {
        return getTarget().isTerminated();
    }

    @Override
    default boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return getTarget().awaitTermination(timeout, unit);
    }

    @Override
    default @Nonnull <T> Future<T> submit(@Nonnull Callable<T> task) {
        return getTarget().submit(instrument(task));
    }

    @Override
    default @Nonnull <T> Future<T> submit(@Nonnull Runnable task, T result) {
        return getTarget().submit(instrument(task), result);
    }

    @Override
    default @Nonnull Future<?> submit(@Nonnull Runnable task) {
        return getTarget().submit(instrument(task));
    }

    @Override
    default @Nonnull <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return getTarget().invokeAll(
                tasks.stream().map(this::instrument).collect(Collectors.toList())
        );
    }

    @Override
    default @Nonnull <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks, long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return getTarget().invokeAll(
                tasks.stream().map(this::instrument).collect(Collectors.toList()), timeout, unit
        );
    }

    @Override
    default @Nonnull <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return getTarget().invokeAny(
                tasks.stream().map(this::instrument).collect(Collectors.toList())
        );
    }

    @Override
    default @Nonnull <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks, long timeout, @Nonnull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return getTarget().invokeAny(
                tasks.stream().map(this::instrument).collect(Collectors.toList()), timeout, unit
        );
    }
}
