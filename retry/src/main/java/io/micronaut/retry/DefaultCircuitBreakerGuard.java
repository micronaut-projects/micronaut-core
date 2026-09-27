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
package io.micronaut.retry;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.intercept.CircuitBreakerRetry;
import io.micronaut.retry.intercept.PolicyRetryStateBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * The default {@link CircuitBreakerGuard}: a {@link CircuitBreakerRetry} over the shared circuit,
 * whose retries are never used.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultCircuitBreakerGuard implements CircuitBreakerGuard {

    private final String name;
    private final CircuitBreakerRetry retryState;

    DefaultCircuitBreakerGuard(String name, CircuitBreakerRetry.Circuit circuit) {
        this.name = name;
        this.retryState = new CircuitBreakerRetry(
            circuit,
            new PolicyRetryStateBuilder(RetryPolicy.builder().build()),
            new GuardMethod(name),
            null,
            true
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CircuitState getState() {
        CircuitState state = retryState.currentState();
        return state == null ? CircuitState.CLOSED : state;
    }

    @Override
    public void acquire() {
        retryState.open();
    }

    @Override
    public void onSuccess() {
        retryState.close(null);
    }

    @Override
    public void onFailure(Throwable failure) {
        retryState.close(Objects.requireNonNull(failure, "failure"));
    }

    @Override
    public String toString() {
        return "CircuitBreakerGuard{" + name + ", " + getState() + '}';
    }

    /**
     * The method the logs and the events of the circuit name.
     *
     * @param name The name of the circuit breaker
     */
    private record GuardMethod(String name) implements ExecutableMethod<Object, Object> {

        @Override
        public Class<Object> getDeclaringType() {
            return Object.class;
        }

        @Override
        public String getMethodName() {
            return name;
        }

        @Override
        public Argument<?>[] getArguments() {
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public Method getTargetMethod() {
            throw new UnsupportedOperationException("No target method for the guard of a circuit breaker");
        }

        @Override
        public ReturnType<Object> getReturnType() {
            return ReturnType.of(Object.class);
        }

        @Override
        public Object invoke(@Nullable Object instance, Object... arguments) {
            throw new UnsupportedOperationException("No invocation for the guard of a circuit breaker");
        }

        @Override
        public String toString() {
            return "CircuitBreaker(" + name + ")";
        }
    }
}
