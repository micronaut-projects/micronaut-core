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

/**
 * The circuit breaker of a gateway route: a named circuit of the
 * {@link io.micronaut.retry.CircuitBreakerRegistry}, which the route shares with every other
 * user of the name, e.g. other routes and methods annotated
 * {@code @CircuitBreaker(name = "...")}, and which is configured under
 * {@code micronaut.retry.circuit-breakers.<name>}.
 *
 * <p>Each attempt of the route acquires the circuit and reports its outcome once the headers of
 * the response arrive: a success is a response with a status below {@code 500}; a failure is a
 * response of {@code 500} or above, which is still relayed, or an attempt that failed. The
 * circuit never waits for a body. While the circuit is open, the route does not call the
 * upstream.</p>
 *
 * <p>When the route cannot relay a response, because the circuit is open or the last attempt
 * failed, the fallback handler answers the request in process, with the failure as the request
 * attribute {@link GatewayAttributes#FAILURE}. Without a fallback, the request fails with an
 * {@link UpstreamException}, {@code 503} for an open circuit. A fallback cannot read a body
 * that an attempt already sent.</p>
 *
 * @param name     The name of the circuit
 * @param fallback The fallback handler, or {@code null} for none
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record GatewayCircuitBreaker(String name, @Nullable AsyncRequestHandler fallback) implements GatewayFilter {

    /**
     * @param name     The name of the circuit
     * @param fallback The fallback handler
     */
    public GatewayCircuitBreaker {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("The name of a circuit breaker is blank");
        }
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
