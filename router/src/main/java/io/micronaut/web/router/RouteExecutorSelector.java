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
package io.micronaut.web.router;

import io.micronaut.core.execution.ImmediateExecutor;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodReference;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.builder.HandlerMethod;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Selects the executor of a route info, with the choice of the route when the route info
 * was built. It holds the executor selector of the services, the method and the choice,
 * not the route: the route info, which lives as long as the router, must not keep the
 * route, and through it the assembly and the other routes, reachable.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class RouteExecutorSelector implements ExecutorSelector {
    private final @Nullable ExecutorSelector executorSelector;
    private final ExecutableMethod<?, ?> method;
    private final @Nullable String executorName;
    private final boolean eventLoop;

    RouteExecutorSelector(@Nullable ExecutorSelector executorSelector,
                          ExecutableMethod<?, ?> method,
                          @Nullable String executorName,
                          boolean eventLoop) {
        this.executorSelector = executorSelector;
        this.method = method;
        this.executorName = executorName;
        this.eventLoop = eventLoop;
    }

    @Override
    public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection) {
        // like @ExecuteOn and @NonBlocking on the method
        String name = executorName;
        if (name != null) {
            return Optional.of(select(name).orElseThrow(() -> new SchedulerConfigurationException(
                this.method, RouteAssembly.NO_EXECUTOR + name)));
        }
        if (eventLoop && threadSelection == ThreadSelection.AUTO) {
            return Optional.empty();
        }
        if (executorSelector != null) {
            // the handler of a located route has the annotations it inherits, e.g. an @ExecuteOn of a group of its locator route
            MethodReference<?, ?> annotated = method instanceof HandlerMethod<?> handler ? handler : this.method;
            return executorSelector.select(annotated, threadSelection);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ExecutorService> select(String name) {
        if (executorSelector != null) {
            return executorSelector.select(name);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Executor selectExecutor(@Nullable MethodReference<?, ?> method, ThreadSelectionConfiguration configuration) {
        if (executorName != null || eventLoop) {
            // selects with the route's choice, see select(MethodReference, ThreadSelection)
            return ExecutorSelector.super.selectExecutor(method, configuration);
        }
        if (executorSelector != null) {
            return executorSelector.selectExecutor(method, configuration);
        } else {
            return ImmediateExecutor.INSTANCE;
        }
    }
}
