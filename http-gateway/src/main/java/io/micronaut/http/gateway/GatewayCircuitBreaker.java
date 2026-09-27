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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * The circuit breaker of a gateway route: a named circuit of the
 * {@link io.micronaut.retry.CircuitBreakerRegistry}, which the route shares with every other
 * user of the name, e.g. other routes and methods annotated
 * {@code @CircuitBreaker(name = "...")}, and which is configured under
 * {@code micronaut.retry.circuit-breakers.<name>}.
 *
 * <p>Each attempt of the route acquires the circuit and reports its outcome once the headers of
 * the response arrive: a failure is an attempt that failed, or a response of one of the
 * {@link #failureStatuses() failure statuses}, {@code 500} and above by default, which is still
 * relayed; any other response is a success. The circuit never waits for a body. While the
 * circuit is open, the route does not call the upstream. The circuit decides when it opens and
 * closes, see {@link io.micronaut.retry.CircuitBreakerWindow} for a circuit configured
 * with a rolling window under {@code micronaut.retry.circuit-breakers.<name>}.</p>
 *
 * <p>When the route cannot relay a response, because the circuit is open or the last attempt
 * failed, or when the response has one of the {@link #fallbackStatuses() fallback statuses},
 * none by default, the fallback handler answers the request in process, with the failure as the
 * request attribute {@link GatewayAttributes#FAILURE}. Without a fallback, the request fails
 * with an {@link UpstreamException}, {@code 503} for an open circuit, and a response of a
 * fallback status is relayed.</p>
 *
 * @param name             The name of the circuit
 * @param fallback         The fallback handler, or {@code null} for none
 * @param failureStatuses  The statuses of a response that count as a failure of the circuit;
 *                         empty for {@code 500} and above
 * @param fallbackStatuses The statuses of a response that the fallback answers for instead
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record GatewayCircuitBreaker(String name,
                                    @Nullable AsyncRequestHandler fallback,
                                    Set<Integer> failureStatuses,
                                    Set<Integer> fallbackStatuses) implements GatewayFilter {

    /**
     * @param name             The name of the circuit
     * @param fallback         The fallback handler
     * @param failureStatuses  The statuses that count as a failure
     * @param fallbackStatuses The statuses the fallback answers for
     */
    public GatewayCircuitBreaker {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("The name of a circuit breaker is blank");
        }
        failureStatuses = Set.copyOf(Objects.requireNonNull(failureStatuses, "failureStatuses"));
        fallbackStatuses = Set.copyOf(Objects.requireNonNull(fallbackStatuses, "fallbackStatuses"));
    }

    /**
     * @param name     The name of the circuit
     * @param fallback The fallback handler, or {@code null} for none
     */
    public GatewayCircuitBreaker(String name, @Nullable AsyncRequestHandler fallback) {
        this(name, fallback, Set.of(), Set.of());
    }

    /**
     * @param statuses The statuses of a response that count as a failure, instead of {@code 500} and above
     * @return A copy with the statuses
     */
    public GatewayCircuitBreaker withFailureStatuses(Integer... statuses) {
        return new GatewayCircuitBreaker(name, fallback, Set.of(statuses), fallbackStatuses);
    }

    /**
     * @param statuses The statuses of a response that the fallback answers for
     * @return A copy with the statuses
     */
    public GatewayCircuitBreaker withFallbackStatuses(Integer... statuses) {
        return new GatewayCircuitBreaker(name, fallback, failureStatuses, Set.of(statuses));
    }

    /**
     * @param status The status of a response
     * @return Whether it counts as a failure of the circuit
     */
    public boolean isFailure(int status) {
        return failureStatuses.isEmpty() ? status >= 500 : failureStatuses.contains(status);
    }

    /**
     * @param status The status of a response
     * @return Whether the fallback answers for it, when there is one
     */
    public boolean fallsBackOn(int status) {
        return fallback != null && fallbackStatuses.contains(status);
    }

    /**
     * @param name The name of the circuit
     * @return A circuit breaker without a fallback
     */
    public static GatewayCircuitBreaker of(String name) {
        return new GatewayCircuitBreaker(name, null);
    }

    /**
     * @param name     The name of the circuit
     * @param fallback The fallback handler
     * @return A circuit breaker with a fallback
     */
    public static GatewayCircuitBreaker of(String name, AsyncRequestHandler fallback) {
        return new GatewayCircuitBreaker(name, Objects.requireNonNull(fallback, "fallback"));
    }
}
