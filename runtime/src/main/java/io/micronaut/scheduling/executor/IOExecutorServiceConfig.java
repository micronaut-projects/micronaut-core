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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.TaskExecutors;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Configures the default I/O thread pool if none is configured by the user.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(missingProperty = ExecutorConfiguration.PREFIX_IO)
@Factory
public class IOExecutorServiceConfig {

    /**
     * @return The default thread pool configurations
     */
    @Singleton
    @Named(TaskExecutors.IO)
    ExecutorConfiguration configuration() {
        return UserExecutorConfiguration.of(TaskExecutors.IO, ExecutorType.CACHED);
    }
}
