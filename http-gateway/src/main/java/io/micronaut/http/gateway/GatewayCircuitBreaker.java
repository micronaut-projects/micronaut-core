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
 * none by default, the fallback handler answers the request in process, with the failure as
 * {@link GatewayRequestContext#failure()}. Without a fallback, the request fails
 * with an {@link UpstreamException}, {@code 503} for an open circuit, and a response of a
 * fallback status is relayed.</p>
 *
 * <p>The fallback reads the body of the request when no attempt sent it: a request without a
 * body, or a circuit that was open before the first attempt. The body an attempt sent is gone:
 * reading it fails.</p>
 *
 * <p>Create one with {@link #of(String)}, {@link #of(String, AsyncRequestHandler)} or
 * {@link #builder(String)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayCircuitBreaker implements GatewayFilter {

    private final String name;
    private final @Nullable AsyncRequestHandler fallback;
    private final Set<Integer> failureStatuses;
    private final Set<Integer> fallbackStatuses;

    private GatewayCircuitBreaker(Builder builder) {
        this.name = builder.name;
        this.fallback = builder.fallback;
        this.failureStatuses = builder.failureStatuses;
        this.fallbackStatuses = builder.fallbackStatuses;
    }

    /**
     * @param name The name of the circuit
     * @return A circuit breaker without a fallback
     */
    public static GatewayCircuitBreaker of(String name) {
        return builder(name).build();
    }

    /**
     * @param name     The name of the circuit
     * @param fallback The fallback handler
     * @return A circuit breaker with a fallback
     */
    public static GatewayCircuitBreaker of(String name, AsyncRequestHandler fallback) {
        return builder(name).fallback(Objects.requireNonNull(fallback, "fallback")).build();
    }

    /**
     * @param name The name of the circuit
     * @return A builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * @return A builder with the settings of this circuit breaker
     */
    public Builder toBuilder() {
        return new Builder(name)
            .fallback(fallback)
            .failureStatuses(failureStatuses)
            .fallbackStatuses(fallbackStatuses);
    }

    /**
     * @return The name of the circuit
     */
    public String name() {
        return name;
    }

    /**
     * @return The fallback handler, or {@code null} for none
     */
    public @Nullable AsyncRequestHandler fallback() {
        return fallback;
    }

    /**
     * @return The statuses of a response that count as a failure of the circuit; empty for
     * {@code 500} and above
     */
    public Set<Integer> failureStatuses() {
        return failureStatuses;
    }

    /**
     * @return The statuses of a response that the fallback answers for instead
     */
    public Set<Integer> fallbackStatuses() {
        return fallbackStatuses;
    }

    /**
     * @param statuses The statuses of a response that count as a failure, instead of {@code 500} and above
     * @return A copy with the statuses
     */
    public GatewayCircuitBreaker withFailureStatuses(Integer... statuses) {
        return toBuilder().failureStatuses(Set.of(statuses)).build();
    }

    /**
     * @param statuses The statuses of a response that the fallback answers for
     * @return A copy with the statuses
     */
    public GatewayCircuitBreaker withFallbackStatuses(Integer... statuses) {
        return toBuilder().fallbackStatuses(Set.of(statuses)).build();
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

    @Override
    public boolean equals(Object o) {
        return o instanceof GatewayCircuitBreaker that
            && name.equals(that.name)
            && Objects.equals(fallback, that.fallback)
            && failureStatuses.equals(that.failureStatuses)
            && fallbackStatuses.equals(that.fallbackStatuses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fallback, failureStatuses, fallbackStatuses);
    }

    @Override
    public String toString() {
        return "CircuitBreaker=" + name;
    }

    /**
     * The builder of {@link GatewayCircuitBreaker}.
     */
    public static final class Builder {
        private final String name;
        private @Nullable AsyncRequestHandler fallback;
        private Set<Integer> failureStatuses = Set.of();
        private Set<Integer> fallbackStatuses = Set.of();

        private Builder(String name) {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("The name of a circuit breaker is blank");
            }
            this.name = name;
        }

        /**
         * @param fallback See {@link GatewayCircuitBreaker#fallback()}
         * @return This builder
         */
        public Builder fallback(@Nullable AsyncRequestHandler fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * @param failureStatuses See {@link GatewayCircuitBreaker#failureStatuses()}
         * @return This builder
         */
        public Builder failureStatuses(Set<Integer> failureStatuses) {
            this.failureStatuses = Set.copyOf(Objects.requireNonNull(failureStatuses, "failureStatuses"));
            return this;
        }

        /**
         * @param fallbackStatuses See {@link GatewayCircuitBreaker#fallbackStatuses()}
         * @return This builder
         */
        public Builder fallbackStatuses(Set<Integer> fallbackStatuses) {
            this.fallbackStatuses = Set.copyOf(Objects.requireNonNull(fallbackStatuses, "fallbackStatuses"));
            return this;
        }

        /**
         * @return The circuit breaker
         */
        public GatewayCircuitBreaker build() {
            return new GatewayCircuitBreaker(this);
        }
    }
}
