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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.LoomSupport;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Constructs {@link ExecutorService} instances based on {@link UserExecutorConfiguration} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ExecutorFactory implements GracefulShutdownCapable {

    private final BeanLocator beanLocator;
    private final ThreadFactory threadFactory;
    private List<GracefulShutdownCapable> gracefulShutdownCapable;

    /**
     *
     * @param beanLocator The bean beanLocator
     * @param threadFactory The factory to create new threads
     * @since 2.0.1
     */
    @Inject
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
            return r -> LoomSupport.unstarted(prefix + ThreadLocalRandom.current().nextInt(), null, r);
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
     * @return A thread pool that creates new threads as needed
     */
    @EachBean(ExecutorConfiguration.class)
    @Bean(preDestroy = "shutdown")
    public ExecutorService executorService(ExecutorConfiguration executorConfiguration) {
        ExecutorType executorType = executorConfiguration.getType();
        switch (executorType) {
            case FIXED:
                return Executors.newFixedThreadPool(executorConfiguration.getNumberOfThreads(), getThreadFactory(executorConfiguration));
            case CACHED:
                return Executors.newCachedThreadPool(getThreadFactory(executorConfiguration));
            case SCHEDULED:
                var exec = new GracefulShutdownCapableScheduledThreadPoolExecutor(executorConfiguration.getCorePoolSize(), getThreadFactory(executorConfiguration));
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
                    return new FastThreadPerTaskExecutor(getThreadFactory(executorConfiguration));
                } else {
                    return LoomSupport.newThreadPerTaskExecutor(getThreadFactory(executorConfiguration));
                }
            default:
                throw new IllegalStateException("Could not create Executor service for enum value: " + executorType);
        }
    }

    private ThreadFactory getThreadFactory(ExecutorConfiguration executorConfiguration) {
        return executorConfiguration
                .getThreadFactoryClass()
                .flatMap(InstantiationUtils::tryInstantiate)
                .map(ThreadFactory.class::cast)
                .orElseGet(() -> {
                    if (beanLocator != null) {
                        if (executorConfiguration.getName() == null) {
                            return beanLocator.getBean(ThreadFactory.class);
                        }
                        return beanLocator.getBean(ThreadFactory.class, Qualifiers.byName(executorConfiguration.getName()));
                    } else {
                        throw new IllegalStateException("No bean factory configured");
                    }
                });
    }

    @Override
    public @NonNull CompletionStage<?> shutdownGracefully() {
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
