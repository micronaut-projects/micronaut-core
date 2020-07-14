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
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.concurrent.*;

/**
 * Constructs {@link ExecutorService} instances based on {@link UserExecutorConfiguration} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ExecutorFactory {

    private final BeanLocator beanLocator;
    private final ThreadFactory threadFactory;

    /**
     *
     * @param beanLocator The bean beanLocator
     * @param threadFactory The factory to create new threads
     */
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
        return configuration.getName() == null ? threadFactory : new NamedThreadFactory(configuration.getName() + "-executor");
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
                return Executors.newScheduledThreadPool(executorConfiguration.getCorePoolSize(), getThreadFactory(executorConfiguration));
            case WORK_STEALING:
                return Executors.newWorkStealingPool(executorConfiguration.getParallelism());

            default:
                throw new IllegalStateException("Could not create Executor service for enum value: " + executorType);
        }
    }

    private ThreadFactory getThreadFactory(ExecutorConfiguration executorConfiguration) {
        return executorConfiguration
                .getThreadFactoryClass()
                .flatMap(InstantiationUtils::tryInstantiate)
                .map(tf -> (ThreadFactory) tf)
                .orElseGet(() -> {
                    if (executorConfiguration.getName() == null) {
                        return beanLocator.getBean(ThreadFactory.class);
                    }
                    return beanLocator.getBean(ThreadFactory.class, Qualifiers.byName(executorConfiguration.getName()));
                });
    }

}
