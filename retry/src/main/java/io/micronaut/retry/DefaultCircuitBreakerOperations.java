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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import reactor.core.publisher.Flux;
import io.micronaut.retry.intercept.CircuitBreakerRetry;
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.MutableRetryState;
import io.micronaut.retry.intercept.PolicyRetryStateBuilder;
import io.micronaut.retry.intercept.RetryEventEmitter;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Internal implementation of {@link CircuitBreakerOperations} that owns a shared circuit state for the created
 * operations instance while delegating execution to the shared retry runner.
 */
@Internal
final class DefaultCircuitBreakerOperations implements CircuitBreakerOperations {

    private static final String NAME = "DefaultCircuitBreakerOperations";

    private final DefaultRetryRunner retryRunner;
    private final CircuitBreakerRetry retryState;
    private final RetryEventEmitter retryEventEmitter;

    DefaultCircuitBreakerOperations(CircuitBreakerPolicy circuitBreakerPolicy,
                                    DefaultRetryRunner retryRunner,
                                    RetryEventEmitter retryEventEmitter) {
        this(circuitBreakerPolicy,
            new CircuitBreakerRetry.Circuit(circuitBreakerPolicy.getResetTimeout().toMillis(), null),
            "programmaticCircuitBreaker",
            null,
            retryRunner,
            retryEventEmitter);
    }

    /**
     * Operations with the retries of a policy over a shared circuit, e.g. of a named circuit
     * breaker.
     *
     * @param circuitBreakerPolicy The policy of the retries
     * @param circuit              The circuit
     * @param name                 The name of the circuit breaker, for the logs and the events
     * @param eventPublisher       To publish the events of the circuit
     * @param retryRunner          The retry runner
     * @param retryEventEmitter    The retry event emitter
     */
    DefaultCircuitBreakerOperations(CircuitBreakerPolicy circuitBreakerPolicy,
                                    CircuitBreakerRetry.Circuit circuit,
                                    String name,
                                    @Nullable ApplicationEventPublisher<Object> eventPublisher,
                                    DefaultRetryRunner retryRunner,
                                    RetryEventEmitter retryEventEmitter) {
        this.retryRunner = retryRunner;
        this.retryEventEmitter = retryEventEmitter;
        this.retryState = new CircuitBreakerRetry(
            circuit,
            new PolicyRetryStateBuilder(circuitBreakerPolicy.asRetryPolicy()),
            new ProgrammaticExecutableMethod(name),
            eventPublisher,
            circuitBreakerPolicy.isThrowWrappedException()
        );
    }

    @Override
    public <T> T execute(Supplier<T> supplier) {
        MutableRetryState invocation = retryState.newInvocation();
        invocation.open();
        return retryRunner.executeSync(supplier, invocation, NAME, retryEventEmitter);
    }

    @Override
    public <T> CompletionStage<T> executeCompletionStage(Supplier<? extends CompletionStage<T>> supplier) {
        MutableRetryState invocation = retryState.newInvocation();
        invocation.open();
        return retryRunner.executeCompletionStage(supplier, invocation, NAME, retryEventEmitter);
    }

    @Override
    public <T> Publisher<T> executePublisher(Supplier<? extends Publisher<T>> supplier) {
        return Flux.defer(() -> {
            MutableRetryState invocation = retryState.newInvocation();
            invocation.open();
            return Flux.from(retryRunner.executePublisher(supplier, invocation, NAME, retryEventEmitter));
        });
    }

    @Override
    public CircuitState currentState() {
        @Nullable CircuitState circuitState = retryState.currentState();
        return circuitState == null ? CircuitState.CLOSED : circuitState;
    }
}
