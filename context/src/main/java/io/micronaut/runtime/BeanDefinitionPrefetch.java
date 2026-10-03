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
package io.micronaut.runtime;

import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * The experimental bean definition prefetch, switched on with the system property
 * {@value #PROPERTY}{@code =true}: Micronaut's own {@link DefaultBeanDefinitionsProvider} runs on
 * the common pool while the main thread configures logging and creates the builder, and
 * {@link Micronaut#start()} hands its result to the context it builds.
 *
 * <p>The task never loads or initializes a bean definition reference itself. It calls the
 * provider that the context would have called, and the first {@link #provide(ClassLoader)} with
 * the task's class loader returns what that call returned or rethrows, unchanged, what it threw.
 * Which failures of a reference Micronaut skips and which ones stop the application is therefore
 * decided by Micronaut, on the thread that met the failure first.</p>
 *
 * <p>This class must not use {@link Micronaut}: it is created inside the static initializer of
 * {@link Micronaut}, and a pool thread that needed that class would wait for the main thread.</p>
 *
 * @since 5.3.0
 */
@NullMarked
@SuppressWarnings({"serial", "java:S1948"}) // A task that is never serialized
final class BeanDefinitionPrefetch extends RecursiveAction implements BeanDefinitionsProvider {

    /**
     * The system property that switches the prefetch on when it is {@code true}.
     */
    static final String PROPERTY = "micronaut.bean-definitions.prefetch";

    /**
     * The fewest common pool threads the prefetch starts with: what a JVM with four processors
     * has. Micronaut loads the references on the pool threads and on the thread that asks for
     * them. With the prefetch, the thread that asks is a pool thread and the main thread only
     * waits, so one thread fewer does that work, which made startup slower with two pool threads.
     */
    static final int MINIMUM_PARALLELISM = 3;

    private static final int OPEN = 0;
    private static final int HANDED_OVER = 1;
    private static final int GIVEN_UP = 2;

    @Nullable
    private final ClassLoader contextClassLoader;
    private final ClassLoader classLoader;
    private final Object lock = new Object();
    // The fields below are guarded by the lock
    private int state = OPEN;
    @Nullable
    private List<BeanDefinitionReference<?>> result;
    @Nullable
    private Throwable failure;

    BeanDefinitionPrefetch(@Nullable ClassLoader contextClassLoader, ClassLoader classLoader) {
        this.contextClassLoader = contextClassLoader;
        this.classLoader = classLoader;
    }

    /**
     * Starts the prefetch, unless the application runs in a native image or the common pool has
     * fewer than {@value #MINIMUM_PARALLELISM} threads. The static initializer of
     * {@link Micronaut} calls this once {@value #PROPERTY} is {@code true}, before it creates its
     * logger.
     *
     * @param classLoader The class loader of the builder, which the context reads the references with
     * @return The running task, or {@code null} when the prefetch stands down
     */
    static @Nullable BeanDefinitionPrefetch start(ClassLoader classLoader) {
        if (NativeImageUtils.inImageCode() || ForkJoinPool.getCommonPoolParallelism() < MINIMUM_PARALLELISM) {
            return null;
        }
        // DefaultBeanContext sets it too. ClassUtils reads it once, and a pool thread can now be the first to initialize ClassUtils
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, StringUtils.TRUE);
        return launch(Thread.currentThread().getContextClassLoader(), classLoader);
    }

    /**
     * Submits a task to the common pool and returns without waiting for it.
     *
     * @param contextClassLoader The context class loader of the thread that starts the prefetch
     * @param classLoader The class loader to read the references with
     * @return The running task
     */
    static BeanDefinitionPrefetch launch(@Nullable ClassLoader contextClassLoader, ClassLoader classLoader) {
        BeanDefinitionPrefetch task = new BeanDefinitionPrefetch(contextClassLoader, classLoader);
        ForkJoinPool.commonPool().execute(task);
        return task;
    }

    /**
     * Builds {@link ConversionService#SHARED}, which the context needs before the references, and
     * then runs Micronaut's provider. Both run with the context class loader of the thread that
     * started the prefetch: {@code StaticOptimizations} and {@code SoftServiceLoader} look their
     * services up there, and a common pool thread carries the system class loader.
     */
    @Override
    @SuppressWarnings("java:S1181") // Whatever the provider throws is rethrown, unchanged, on the thread that builds the context
    protected void compute() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(contextClassLoader);
        List<BeanDefinitionReference<?>> references = null;
        Throwable thrown = null;
        try {
            ConversionService _ = ConversionService.SHARED;
            references = new DefaultBeanDefinitionsProvider().provide(classLoader);
        } catch (Throwable t) {
            thrown = t;
        } finally {
            thread.setContextClassLoader(previous);
        }
        synchronized (lock) {
            if (state != GIVEN_UP) {
                result = references;
                failure = thrown;
            }
        }
    }

    /**
     * The first call with the task's class loader waits for the task, then returns its references
     * or rethrows what it threw. Every other call goes to {@link DefaultBeanDefinitionsProvider}:
     * another class loader, a later context, the read after a reset, or any call after
     * {@link #giveUp()}.
     *
     * @param classLoader The class loader to use for loading bean definitions
     * @return The bean definition references
     */
    @Override
    @SuppressWarnings("ReferenceEquality") // The task read the references with this very class loader
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        if (classLoader != this.classLoader || !handOver()) {
            return new DefaultBeanDefinitionsProvider().provide(classLoader);
        }
        // compute() catches everything, so this returns normally
        join();
        List<BeanDefinitionReference<?>> references;
        Throwable thrown;
        synchronized (lock) {
            references = result;
            thrown = failure;
            result = null;
            failure = null;
        }
        if (thrown != null) {
            return ExceptionUtils.sneakyThrow(thrown);
        }
        return Objects.requireNonNull(references);
    }

    private boolean handOver() {
        synchronized (lock) {
            if (state != OPEN) {
                return false;
            }
            state = HANDED_OVER;
            return true;
        }
    }

    /**
     * Gives the result up, now or when the task finishes, unless a context took it. Never waits
     * for the task.
     *
     * @return What the task threw, when it finished without being handed over, or {@code null}
     */
    @Nullable
    Throwable giveUp() {
        synchronized (lock) {
            if (state != OPEN) {
                return null;
            }
            state = GIVEN_UP;
            Throwable thrown = failure;
            result = null;
            failure = null;
            return thrown;
        }
    }
}
