/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev;

import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.loader.DevClassLoader;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the threads of Reactor's schedulers from keeping a generation. Reactor's shared schedulers, such as
 * {@code Schedulers.boundedElastic()}, live as long as the JVM, and create their threads when work first needs one: a
 * thread created from a generation's thread takes that generation's loader as its context class loader, and keeps the
 * generation for as long as the thread lives, while the work of later generations sees the classes of that first one.
 * Starting the threads ahead of the first generation would not do: {@code boundedElastic} creates a worker whenever
 * its busy workers do not suffice, and would start threads an application never uses.
 *
 * <p>The factory of the schedulers is replaced instead, so that each thread they create, and the evictor
 * {@code boundedElastic} starts as it initializes, has the parent tier's loader as its context class loader. A task
 * runs with the current generation's loader as its thread's context class loader, as on the application's thread, so
 * that work on a scheduler sees the application's classes and resources as it would outside development mode, and the
 * parent tier's again once it is done. The development runtime's own loader is not given to a thread: the JVM records
 * the loader {@code Class.forName} is called with as an initiating loader, which would keep the classes of the
 * generation current then. An application that sets a factory of its own replaces this one.</p>
 *
 * <p>Uses Reactor: only called once its presence on the parent tier is known.</p>
 */
@Internal
final class ReactorThreads {

    private static final String HOOK = "micronaut-dev-generation";
    private static final AtomicReference<DevClassLoader> INSTALLED = new AtomicReference<>();

    private ReactorThreads() {
    }

    /**
     * Sets the factory of Reactor's schedulers to one whose threads have the parent tier's loader, and runs each task
     * with the current generation's loader, unless the given runtime's loader is set already.
     *
     * @param runtime    The loader of the development runtime, whose current generation a task runs with
     * @param parentTier The loader of the parent tier
     */
    static void use(DevClassLoader runtime, ClassLoader parentTier) {
        DevClassLoader previous = INSTALLED.getAndSet(runtime);
        if (!runtime.equals(previous)) {
            // disposes the shared schedulers created before, if any: they are created again, from this factory
            Schedulers.setFactory(new DevFactory(runtime, parentTier));
            Schedulers.onScheduleHook(HOOK, task -> new InGeneration(task, runtime));
        }
    }

    /**
     * Restores Reactor's own factory, if the given runtime's is set, as that runtime closes: the shared schedulers it
     * created are disposed, and their threads end.
     *
     * @param runtime The loader of the development runtime
     */
    static void release(DevClassLoader runtime) {
        if (INSTALLED.compareAndSet(runtime, null)) {
            Schedulers.resetOnScheduleHook(HOOK);
            Schedulers.resetFactory();
        }
    }

    /**
     * Creates the schedulers Reactor's own factory creates, with thread factories that give their threads the parent
     * tier's loader.
     */
    private static final class DevFactory implements Schedulers.Factory {

        private final DevClassLoader runtime;
        private final ClassLoader parentTier;

        DevFactory(DevClassLoader runtime, ClassLoader parentTier) {
            this.runtime = runtime;
            this.parentTier = parentTier;
        }

        @Override
        public Scheduler newBoundedElastic(int threadCap, int queuedTaskCap, ThreadFactory threadFactory, int ttlSeconds) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(parentTier);
            try {
                // the evictor's thread starts as the scheduler initializes, from a thread factory of Reactor's own:
                // initialized here, it starts with the parent tier's loader; Schedulers initializes it again, a no-op
                Scheduler scheduler = Schedulers.Factory.super.newBoundedElastic(threadCap, queuedTaskCap, new ParentTierThreads(threadFactory, parentTier), ttlSeconds);
                scheduler.init();
                return scheduler;
            } finally {
                thread.setContextClassLoader(previous);
            }
        }

        @Override
        public Scheduler newThreadPerTaskBoundedElastic(int threadCap, int queuedTaskCap, ThreadFactory threadFactory) {
            // a thread per task, which ends with it, and a task the schedule hook does not see: the thread has the
            // loader of the generation current when the task is scheduled
            return Schedulers.Factory.super.newThreadPerTaskBoundedElastic(threadCap, queuedTaskCap, runnable -> {
                Thread created = threadFactory.newThread(runnable);
                if (created != null) {
                    created.setContextClassLoader(runtime.current());
                }
                return created;
            });
        }

        @Override
        public Scheduler newParallel(int parallelism, ThreadFactory threadFactory) {
            return Schedulers.Factory.super.newParallel(parallelism, new ParentTierThreads(threadFactory, parentTier));
        }

        @Override
        public Scheduler newSingle(ThreadFactory threadFactory) {
            return Schedulers.Factory.super.newSingle(new ParentTierThreads(threadFactory, parentTier));
        }
    }

    /**
     * A thread factory whose threads have the parent tier's loader as their context class loader.
     *
     * @param delegate   The thread factory Reactor chose
     * @param parentTier The loader of the parent tier
     */
    private record ParentTierThreads(ThreadFactory delegate, ClassLoader parentTier) implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = delegate.newThread(runnable);
            if (thread != null) {
                thread.setContextClassLoader(parentTier);
            }
            return thread;
        }
    }

    /**
     * A task that runs with the loader of the generation current when it runs as its thread's context class loader,
     * and restores the thread's own once it is done.
     *
     * @param task    The task
     * @param runtime The loader of the development runtime
     */
    private record InGeneration(Runnable task, DevClassLoader runtime) implements Runnable {

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(runtime.current());
            try {
                task.run();
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }
}
