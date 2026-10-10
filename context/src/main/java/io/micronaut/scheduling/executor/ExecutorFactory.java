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
package io.micronaut.scheduling.executor;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Constructs {@link ExecutorService} instances based on {@link UserExecutorConfiguration} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ExecutorFactory implements GracefulShutdownCapable {

    @Nullable
    private final BeanLocator beanLocator;
    private final ThreadFactory threadFactory;
    @Nullable
    private List<GracefulShutdownCapable> gracefulShutdownCapable;

    /**
     * Creates the factory. It holds no bean locator: an executor service receives the thread factory of its
     * configuration, so that neither the executor service nor the factory holds the context. A bean that received an
     * executor service can then be retained across a restart in development mode, with the executor service.
     *
     * @param threadFactory The factory to create new threads
     * @since 5.3.0
     */
    @Inject
    public ExecutorFactory(ThreadFactory threadFactory) {
        this.beanLocator = null;
        this.threadFactory = threadFactory;
    }

    /**
     *
     * @param beanLocator The bean beanLocator
     * @param threadFactory The factory to create new threads
     * @since 2.0.1
     * @deprecated The executor services receive the thread factory of their configuration; use {@link #ExecutorFactory(ThreadFactory)}
     */
    @Deprecated(since = "5.3.0")
    public ExecutorFactory(BeanLocator beanLocator, ThreadFactory threadFactory) {
        this.beanLocator = beanLocator;
        this.threadFactory = threadFactory;
    }

    /**
     * Constructs an executor thread factory.
     *
     * @param configuration The configuration
     * @return The thread factory
     */
    @EachBean(ExecutorConfiguration.class)
    protected ThreadFactory eventLoopGroupThreadFactory(ExecutorConfiguration configuration) {
        String name = configuration.getName();
        if (configuration.isVirtual()) {
            if (name == null) {
                name = "virtual";
            }
            String prefix = name + "-executor-";
            return r -> Thread.ofVirtual().name(prefix + ThreadLocalRandom.current().nextInt()).unstarted(r);
        }
        if (name != null) {
            return new NamedThreadFactory(name + "-executor");
        }
        return threadFactory;
    }

    /**
     * Create the ExecutorService with the given configuration.
     *
     * @param executorConfiguration The configuration to create a thread pool that creates new threads as needed
     * @param namedThreadFactory The thread factory bean of the same name as the configuration, which
     * {@link #eventLoopGroupThreadFactory(ExecutorConfiguration)} creates unless the application replaced it; used when
     * the configuration has a name and names no thread factory class
     * @return A thread pool that creates new threads as needed
     * @since 5.3.0
     */
    @EachBean(ExecutorConfiguration.class)
    @Bean(preDestroy = "shutdown")
    public ExecutorService executorService(ExecutorConfiguration executorConfiguration, @Parameter ThreadFactory namedThreadFactory) {
        // the thread factories the bean locator resolved: the primary one for a configuration without a name
        ThreadFactory configured = executorConfiguration.getName() == null ? threadFactory : namedThreadFactory;
        return createExecutorService(executorConfiguration, () -> configured);
    }

    /**
     * Create the ExecutorService with the given configuration.
     *
     * @param executorConfiguration The configuration to create a thread pool that creates new threads as needed
     * @return A thread pool that creates new threads as needed
     * @deprecated The executor service receives the thread factory of its configuration; use {@link #executorService(ExecutorConfiguration, ThreadFactory)}
     */
    @Deprecated(since = "5.3.0")
    public ExecutorService executorService(ExecutorConfiguration executorConfiguration) {
        return createExecutorService(executorConfiguration, () -> lookupThreadFactory(executorConfiguration));
    }

    private ExecutorService createExecutorService(ExecutorConfiguration executorConfiguration, Supplier<ThreadFactory> threadFactory) {
        ExecutorType executorType = executorConfiguration.getType();
        switch (executorType) {
            case FIXED:
                return Executors.newFixedThreadPool(executorConfiguration.getNumberOfThreads(), getThreadFactory(executorConfiguration, threadFactory));
            case CACHED:
                return Executors.newCachedThreadPool(getThreadFactory(executorConfiguration, threadFactory));
            case SCHEDULED:
                var exec = new GracefulShutdownCapableScheduledThreadPoolExecutor(executorConfiguration.getCorePoolSize(), getThreadFactory(executorConfiguration, threadFactory));
                synchronized (this) {
                    if (gracefulShutdownCapable == null) {
                        gracefulShutdownCapable = new ArrayList<>();
                    }
                    gracefulShutdownCapable.add(exec);
                }
                return exec;
            case WORK_STEALING:
                return Executors.newWorkStealingPool(executorConfiguration.getParallelism());
            case THREAD_PER_TASK:
                if ("false".equals(System.getProperty("jdk.trackAllThreads"))) {
                    return new FastThreadPerTaskExecutor(getThreadFactory(executorConfiguration, threadFactory));
                } else {
                    return Executors.newThreadPerTaskExecutor(getThreadFactory(executorConfiguration, threadFactory));
                }
            default:
                throw new IllegalStateException("Could not create Executor service for enum value: " + executorType);
        }
    }

    private static ThreadFactory getThreadFactory(ExecutorConfiguration executorConfiguration, Supplier<ThreadFactory> threadFactory) {
        return executorConfiguration
                .getThreadFactoryClass()
                .flatMap(InstantiationUtils::tryInstantiate)
                .map(ThreadFactory.class::cast)
                .orElseGet(threadFactory);
    }

    private ThreadFactory lookupThreadFactory(ExecutorConfiguration executorConfiguration) {
        if (beanLocator == null) {
            throw new IllegalStateException("No bean factory configured");
        }
        if (executorConfiguration.getName() == null) {
            return beanLocator.getBean(ThreadFactory.class);
        }
        return beanLocator.getBean(ThreadFactory.class, Qualifiers.byName(executorConfiguration.getName()));
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        List<GracefulShutdownCapable> copy;
        synchronized (this) {
            if (gracefulShutdownCapable == null) {
                return CompletableFuture.completedFuture(null);
            }
            copy = new ArrayList<>(gracefulShutdownCapable);
        }
        return GracefulShutdownCapable.shutdownAll(copy.stream());
    }

    @Override
    public OptionalLong reportActiveTasks() {
        List<GracefulShutdownCapable> copy;
        synchronized (this) {
            if (gracefulShutdownCapable == null) {
                return OptionalLong.empty();
            }
            copy = new ArrayList<>(gracefulShutdownCapable);
        }
        return GracefulShutdownCapable.combineActiveTasks(copy);
    }
}
