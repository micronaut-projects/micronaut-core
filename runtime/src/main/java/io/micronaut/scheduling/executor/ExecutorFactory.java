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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.reflect.InstantiationUtils;

import java.util.concurrent.*;

/**
 * Constructs {@link ExecutorService} instances based on {@link UserExecutorConfiguration} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ExecutorFactory {

    private final ThreadFactory threadFactory;

    /**
     *
     * @param threadFactory The factory to create new threads
     */
    public ExecutorFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
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
                return executorConfiguration
                    .getThreadFactoryClass()
                    .flatMap(InstantiationUtils::tryInstantiate)
                    .map(factory -> Executors.newFixedThreadPool(executorConfiguration.getNumberOfThreads(), factory))
                    .orElse(Executors.newFixedThreadPool(executorConfiguration.getNumberOfThreads(), threadFactory));

            case CACHED:
                return executorConfiguration
                    .getThreadFactoryClass()
                    .flatMap(InstantiationUtils::tryInstantiate)
                    .map(Executors::newCachedThreadPool)
                    .orElse(Executors.newCachedThreadPool(threadFactory));

            case SCHEDULED:
                return executorConfiguration
                    .getThreadFactoryClass()
                    .flatMap(InstantiationUtils::tryInstantiate)
                    .map(factory -> Executors.newScheduledThreadPool(executorConfiguration.getCorePoolSize(), factory))
                    .orElse(Executors.newScheduledThreadPool(executorConfiguration.getCorePoolSize(), threadFactory));

            case WORK_STEALING:
                return Executors.newWorkStealingPool(executorConfiguration.getParallelism());

            default:
                throw new IllegalStateException("Could not create Executor service for enum value: " + executorType);
        }
    }
}
