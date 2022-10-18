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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.LoomSupport;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;

/**
 * Configures the default I/O thread pool if none is configured by the user.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public final class IOExecutorServiceConfig {

    /**
     * @return The default IO pool configuration
     */
    @Singleton
    @Named(TaskExecutors.IO)
    @Requires(missingProperty = ExecutorConfiguration.PREFIX_IO)
    ExecutorConfiguration io() {
        return UserExecutorConfiguration.of(TaskExecutors.IO, ExecutorType.CACHED);
    }

    /**
     * @return The default virtual executor configuration
     */
    @Singleton
    @Named(TaskExecutors.VIRTUAL)
    @Requires(
        missingProperty = ExecutorConfiguration.PREFIX + "." + TaskExecutors.VIRTUAL,
        condition = LoomSupport.LoomCondition.class)
    ExecutorConfiguration virtual() {
        // sanity check
        LoomSupport.checkSupported();
        UserExecutorConfiguration cfg = UserExecutorConfiguration.of(TaskExecutors.VIRTUAL, ExecutorType.THREAD_PER_TASK);
        cfg.setVirtual(true);
        return cfg;
    }

    /**
     * The blocking executor.
     *
     * @param io IO executor (fallback)
     * @param virtual Virtual thread executor (used if available)
     * @return The blocking executor
     */
    @Singleton
    @Named(TaskExecutors.BLOCKING)
    @Requires(missingProperty = ExecutorConfiguration.PREFIX + "." + TaskExecutors.BLOCKING)
    ExecutorService blocking(
        @Named(TaskExecutors.IO) BeanProvider<ExecutorService> io,
        @Named(TaskExecutors.VIRTUAL) BeanProvider<ExecutorService> virtual
    ) {
        if (virtual.isPresent()) {
            return virtual.get();
        } else {
            return io.get();
        }
    }
}
