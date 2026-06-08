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
package io.micronaut.retry;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.RetryEventEmitter;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Internal factory that creates reusable programmatic circuit breaker operations backed by Micronaut scheduler infrastructure.
 */
@Internal
@Singleton
final class DefaultCircuitBreakerOperationsFactory implements CircuitBreakerOperationsFactory {

    private static final RetryEventEmitter NO_OP_EVENT_EMITTER = (retryState, exception) -> { };

    private final ScheduledExecutorService executorService;

    DefaultCircuitBreakerOperationsFactory(@Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.executorService = (ScheduledExecutorService) executorService;
    }

    @Override
    public CircuitBreakerOperations createCircuitBreakerOperations(CircuitBreakerPolicy circuitBreakerPolicy) {
        return new DefaultCircuitBreakerOperations(circuitBreakerPolicy, new DefaultRetryRunner(executorService, Thread::sleep), NO_OP_EVENT_EMITTER);
    }
}
