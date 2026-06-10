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
package io.micronaut.context.python;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Context;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Applies configured asyncio settings to generated static bridge methods.
 */
@Context
@Singleton
final class PythonAsyncioRuntimeConfigurer {

    @Inject
    PythonAsyncioRuntimeConfigurer(PythonAsyncioConfiguration configuration,
                                   Collection<PythonEventLoopProvider> eventLoopProviders,
                                   @Nullable @Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> executorServiceProvider) {
        PythonAsyncioRuntime.setEnabled(configuration.enabled());
        PythonAsyncioRuntime.setEventLoopProviders(eventLoopProviders);
        PythonAsyncioRuntime.setExecutorService(null);
        PythonAsyncioRuntime.setExecutorServiceProvider(executorServiceProvider);
    }

    /**
     * Reset static runtime state when the owning application context closes.
     */
    @PreDestroy
    void reset() {
        PythonAsyncioRuntime.setEnabled(true);
        PythonAsyncioRuntime.setEventLoopProviders(List.of());
        PythonAsyncioRuntime.setExecutorService(null);
        PythonAsyncioRuntime.setExecutorServiceProvider(null);
    }
}
