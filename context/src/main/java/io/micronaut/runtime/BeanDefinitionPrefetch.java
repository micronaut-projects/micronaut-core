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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * The experimental bean definition prefetch, switched on with the system property
 * {@value #PROPERTY}{@code =true}: Micronaut's own {@link DefaultBeanDefinitionsProvider} runs on
 * the common pool while the main thread creates the builder, and {@link Micronaut#start()} hands
 * its result to the context it builds.
 *
 * <p>The task never initializes a bean definition reference itself. It calls the provider that
 * the context would have called, and the first {@link #provide(ClassLoader)} with the task's class
 * loader returns what that call returned or rethrows, unchanged, what it threw. Which failures of
 * a reference Micronaut skips and which ones stop the application is therefore decided by
 * Micronaut, on the thread that met the failure first. Before the task starts, the prefetch only
 * loads and links the reference classes, which runs none of their code.</p>
 *
 * <p>This class must not use {@link Micronaut}: it is created inside the static initializer of
 * {@link Micronaut}, and a pool thread that needed that class would wait for the main thread.</p>
 *
 * @since 5.3.0
 */
@Experimental
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
    private final CountDownLatch converted = new CountDownLatch(1);
    private volatile boolean converting;
    // Written before converted counts down, read after it has
    @Nullable
    private Throwable conversionFailure;
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
     * Creates the prefetch, unless the application runs in a native image or the common pool has
     * fewer than {@value #MINIMUM_PARALLELISM} threads, and starts loading the bean definition
     * reference classes on the common pool. The static initializer of {@link Micronaut} calls
     * this once {@value #PROPERTY} is {@code true}, before it creates its logger, which usually
     * configures logging, and calls {@link #submit()} after. In between, the pool only loads and
     * links classes, which runs none of their code, so no pool thread creates a logger while
     * logging is being configured.
     *
     * @param classLoader The class loader of the builder, which the context reads the references with
     * @return The task, not submitted yet, or {@code null} when the prefetch stands down
     */
    static @Nullable BeanDefinitionPrefetch start(ClassLoader classLoader) {
        if (NativeImageUtils.inImageCode() || ForkJoinPool.getCommonPoolParallelism() < MINIMUM_PARALLELISM) {
            return null;
        }
        ForkJoinPool.commonPool().execute(new Preload(classLoader, null));
        return new BeanDefinitionPrefetch(Thread.currentThread().getContextClassLoader(), classLoader);
    }

    /**
     * Submits the task to the common pool and returns without waiting for it. {@link Micronaut}
     * calls this once it has created its logger.
     *
     * <p>Before, this sets {@link ClassUtils#PROPERTY_MICRONAUT_CLASSLOADER_LOGGING}, as the
     * context does when it is created, and initializes {@link ClassUtils} on the calling thread.
     * {@link ClassUtils#REFLECTION_LOGGER} is then created there, with logging configured, rather
     * than on the first pool thread that initializes a bean definition reference that uses
     * {@link ClassUtils}, which can be before the context is created.</p>
     */
    void submit() {
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, StringUtils.TRUE);
        Logger _ = ClassUtils.REFLECTION_LOGGER;
        ForkJoinPool.commonPool().execute(this);
    }

    /**
     * Builds {@link ConversionService#SHARED}, which the context needs before the references, and
     * then runs Micronaut's provider. Both run with the context class loader of the thread that
     * started the prefetch, which code that names no class loader uses, such as
     * {@link ClassUtils#forName(String, ClassLoader)} with {@code null}: a common pool thread
     * carries the system class loader.
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
            if (convert()) {
                references = new DefaultBeanDefinitionsProvider().provide(classLoader);
            }
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
     * Builds {@link ConversionService#SHARED}. A failure, such as a {@code TypeConverterRegistrar}
     * that throws, leaves the class of the conversion service erroneous: the context then fails
     * on it before it asks for the references, and {@link #conversionFailure(Throwable)} returns
     * what building it threw, for {@link Micronaut} to rethrow. The provider does not run.
     *
     * @return Whether the shared conversion service was built
     */
    @SuppressWarnings("java:S1181") // Kept for Micronaut, which rethrows it unchanged
    private boolean convert() {
        converting = true;
        try {
            ConversionService _ = ConversionService.SHARED;
        } catch (Throwable t) {
            conversionFailure = t;
        } finally {
            converted.countDown();
        }
        return conversionFailure == null;
    }

    /**
     * What building {@link ConversionService#SHARED} threw on the task's thread, when the context
     * failed on the class that this left erroneous. When the task has started building it, this
     * waits until it is done: a thread that met the class of the conversion service erroneous can
     * get there first.
     *
     * @param thrown What building the context threw
     * @return What building the shared conversion service threw on the task's thread, or
     * {@code null} when the context failed otherwise, including when the calling thread ran the
     * initializer of the conversion service itself
     */
    @Nullable
    Throwable conversionFailure(Throwable thrown) {
        if (!converting
            || !(thrown instanceof NoClassDefFoundError)
            || !("Could not initialize class " + ConversionService.class.getName()).equals(thrown.getMessage())) {
            // The task has not read the conversion service, or the context failed on its own
            return null;
        }
        try {
            converted.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return conversionFailure;
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
        if (thrown == null) {
            // Set before the task finished, if the provider did not run
            thrown = conversionFailure;
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
     * @return What the provider threw, when the task finished without being handed over, or
     * {@code null}
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

    /**
     * Loads and links the bean definition reference classes without initializing them, which runs
     * none of their code, so that the provider finds them loaded. What fails here is left for the
     * provider to meet again, on its own thread and in its own order.
     */
    private static final class Preload extends RecursiveAction {

        private final ClassLoader classLoader;
        // A reference class to load, or null to list them and load each
        @Nullable
        private final String className;

        Preload(ClassLoader classLoader, @Nullable String className) {
            this.classLoader = classLoader;
            this.className = className;
        }

        @Override
        @SuppressWarnings("NoReflection") // Loads the classes as MicronautMetaServiceLoaderUtils does, without initializing them
        protected void compute() {
            if (className == null) {
                Set<String> classNames;
                try {
                    classNames = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, BeanDefinitionReference.class.getName());
                } catch (IOException | RuntimeException e) {
                    return;
                }
                List<Preload> tasks = new ArrayList<>(classNames.size());
                for (String name : classNames) {
                    tasks.add(new Preload(classLoader, name));
                }
                invokeAll(tasks);
                return;
            }
            try {
                // Links the class, as the provider does before it initializes it
                MethodHandles.publicLookup().findConstructor(Class.forName(className, false, classLoader), MethodType.methodType(void.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // The provider meets it again
            }
        }
    }
}
