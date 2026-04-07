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
import reactor.core.publisher.Flux;
import io.micronaut.retry.intercept.CircuitBreakerRetry;
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.PolicyRetryStateBuilder;
import io.micronaut.retry.intercept.RetryEventEmitter;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Internal implementation of {@link CircuitBreakerOperations} that owns a shared circuit state for the created
 * operations instance while delegating execution to the shared retry runner.
 */
@Internal
final class DefaultCircuitBreakerOperations implements CircuitBreakerOperations {

    private final DefaultRetryRunner retryRunner;
    private final CircuitBreakerRetry retryState;
    private final RetryEventEmitter retryEventEmitter;
    private final ExecutableMethod<Object, Object> executableMethod = new ProgrammaticExecutableMethod();

    DefaultCircuitBreakerOperations(CircuitBreakerPolicy circuitBreakerPolicy,
                                    DefaultRetryRunner retryRunner,
                                    RetryEventEmitter retryEventEmitter) {
        this.retryRunner = retryRunner;
        this.retryEventEmitter = retryEventEmitter;
        this.retryState = new CircuitBreakerRetry(
            circuitBreakerPolicy.getResetTimeout().toMillis(),
            new PolicyRetryStateBuilder(circuitBreakerPolicy.asRetryPolicy()),
            executableMethod,
            null,
            circuitBreakerPolicy.isThrowWrappedException()
        );
    }

    @Override
    public <T> T execute(Supplier<T> supplier) {
        retryState.open();
        return retryRunner.executeSync(supplier, retryState, DefaultCircuitBreakerOperations.class.getSimpleName(), retryEventEmitter);
    }

    @Override
    public <T> CompletionStage<T> executeCompletionStage(Supplier<? extends CompletionStage<T>> supplier) {
        retryState.open();
        return retryRunner.executeCompletionStage(supplier, retryState, DefaultCircuitBreakerOperations.class.getSimpleName(), retryEventEmitter);
    }

    @Override
    public <T> Publisher<T> executePublisher(Supplier<? extends Publisher<T>> supplier) {
        return Flux.defer(() -> {
            retryState.open();
            return Flux.from(retryRunner.executePublisher(supplier, retryState, DefaultCircuitBreakerOperations.class.getSimpleName(), retryEventEmitter));
        });
    }

    @Override
    public CircuitState currentState() {
        @Nullable CircuitState circuitState = retryState.currentState();
        return circuitState == null ? CircuitState.CLOSED : circuitState;
    }

    private static final class ProgrammaticExecutableMethod implements ExecutableMethod<Object, Object> {

        @Override
        public Class<Object> getDeclaringType() {
            return Object.class;
        }

        @Override
        public String getMethodName() {
            return "programmaticCircuitBreaker";
        }

        @Override
        public Argument<?>[] getArguments() {
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public Method getTargetMethod() {
            throw new UnsupportedOperationException("No target method for programmatic circuit breaker executable method");
        }

        @Override
        public ReturnType<Object> getReturnType() {
            return ReturnType.of(Object.class);
        }

        @Override
        public Object invoke(@Nullable Object instance, Object... arguments) {
            throw new UnsupportedOperationException("No direct invocation for programmatic circuit breaker executable method");
        }
    }
}
