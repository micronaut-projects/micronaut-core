/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.executor;

import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.core.reflect.InstantiationUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Constructs {@link ExecutorService} instances based on {@link ExecutorConfiguration} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ExecutorFactory {

    @ForEach(ExecutorConfiguration.class)
    @Bean(preDestroy = "shutdown")
    public ExecutorService executorService(ExecutorConfiguration executorConfiguration) {
        ExecutorType executorType = executorConfiguration.getType();
        switch (executorType) {
            case FIXED:
                return executorConfiguration.getThreadFactoryClass()
                            .flatMap(InstantiationUtils::tryInstantiate)
                            .map(factory -> Executors.newFixedThreadPool(executorConfiguration.getParallelism(), factory))
                            .orElse(Executors.newFixedThreadPool(executorConfiguration.getNumberOfThreads()));
            case CACHED:
                return executorConfiguration.getThreadFactoryClass()
                        .flatMap(InstantiationUtils::tryInstantiate)
                        .map(Executors::newCachedThreadPool)
                        .orElse(Executors.newCachedThreadPool());
            case SCHEDULED:
                return executorConfiguration.getThreadFactoryClass()
                        .flatMap(InstantiationUtils::tryInstantiate)
                        .map(factory -> Executors.newScheduledThreadPool(executorConfiguration.getParallelism(), factory))
                        .orElse(Executors.newScheduledThreadPool(executorConfiguration.getCorePoolSize()));
            case WORK_STEALING:
                return Executors.newWorkStealingPool(executorConfiguration.getParallelism());

        }
         throw new IllegalStateException("Could not create Executor service for enum value: " + executorType );
    }


}
