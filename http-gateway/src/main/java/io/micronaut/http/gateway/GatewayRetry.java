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
import io.micronaut.http.HttpMethod;
import io.micronaut.retry.RetryPolicy;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * When a gateway route sends a request again, to another instance of a {@code lb://} service
 * when it has one: only when the request can be sent again, and never once the headers of a
 * response arrived.
 *
 * <p>A request is sent again when all of these hold:</p>
 * <ul>
 *     <li>attempts are left, see {@link #retries()};</li>
 *     <li>its body can be sent again: it has none, or the server had received all of it when the
 *     route was called, e.g. a small body; a body that still streams in is moved into the first
 *     attempt and never sent twice;</li>
 *     <li>and either the upstream provably did not process it, an
 *     {@link io.micronaut.http.client.exceptions.UnprocessedRequestException}, e.g. the
 *     connection was refused or the request was never written, for any method; or its method is
 *     one of {@link #methods()} and the attempt failed before the headers of a response arrived,
 *     e.g. a reset or a {@link GatewayRoute#getResponseTimeout() response timeout}, or answered
 *     with one of {@link #statuses()}, whose response is closed.</li>
 * </ul>
 * The delay before each retry follows a {@link RetryPolicy}: {@link #delay()}, multiplied by
 * {@link #multiplier()} for each further retry, with a {@link #jitter()}.
 *
 * <p>Create one with {@link #of(int)} or {@link #builder(int)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayRetry implements GatewayFilter {

    /**
     * The default methods.
     */
    public static final Set<String> DEFAULT_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name());
    /**
     * The default statuses.
     */
    public static final Set<Integer> DEFAULT_STATUSES = Set.of(502, 503, 504);
    /**
     * The default delay.
     */
    public static final Duration DEFAULT_DELAY = Duration.ofMillis(50);
    /**
     * The default multiplier.
     */
    public static final double DEFAULT_MULTIPLIER = 2;

    private final int retries;
    private final Set<String> methods;
    private final Set<Integer> statuses;
    private final Duration delay;
    private final double multiplier;
    private final double jitter;

    private GatewayRetry(Builder builder) {
        this.retries = builder.retries;
        this.methods = Set.copyOf(builder.methods);
        this.statuses = Set.copyOf(builder.statuses);
        this.delay = builder.delay;
        this.multiplier = builder.multiplier;
        this.jitter = builder.jitter;
    }

    /**
     * @param retries The number of retries
     * @return The retries with the defaults
     */
    public static GatewayRetry of(int retries) {
        return builder(retries).build();
    }

    /**
     * @param retries The number of times a request is sent again, at least 1
     * @return A builder with the defaults
     */
    public static Builder builder(int retries) {
        return new Builder(retries);
    }

    /**
     * @return A builder with the settings of these retries
     */
    public Builder toBuilder() {
        return new Builder(retries)
            .methods(methods)
            .statuses(statuses)
            .backoff(delay, multiplier)
            .jitter(jitter);
    }

    /**
     * @return The number of times a request is sent again, at least 1
     */
    public int retries() {
        return retries;
    }

    /**
     * @return The methods that are sent again after a failure before the headers or a retryable
     * status, upper case; the idempotent methods {@code GET}, {@code HEAD}, {@code OPTIONS},
     * {@code PUT}, {@code DELETE} and {@code TRACE} are safe, the default is {@code GET} and
     * {@code HEAD}
     */
    public Set<String> methods() {
        return methods;
    }

    /**
     * @return The statuses of a response that sends a request of {@link #methods()} again,
     * default {@code 502}, {@code 503} and {@code 504}
     */
    public Set<Integer> statuses() {
        return statuses;
    }

    /**
     * @return The delay before the first retry, default 50ms
     */
    public Duration delay() {
        return delay;
    }

    /**
     * @return The multiplier of the delay for each further retry, default 2
     */
    public double multiplier() {
        return multiplier;
    }

    /**
     * @return The jitter factor of the delays, between 0 and 1, default 0
     */
    public double jitter() {
        return jitter;
    }

    /**
     * @param methods The methods, see {@link #methods()}
     * @return A copy with the methods
     */
    public GatewayRetry withMethods(String... methods) {
        return toBuilder().methods(Set.of(methods)).build();
    }

    /**
     * @param statuses The statuses, see {@link #statuses()}
     * @return A copy with the statuses
     */
    public GatewayRetry withStatuses(Integer... statuses) {
        return toBuilder().statuses(Set.of(statuses)).build();
    }

    /**
     * @param delay      The delay before the first retry
     * @param multiplier The multiplier for each further retry
     * @return A copy with the backoff
     */
    public GatewayRetry withBackoff(Duration delay, double multiplier) {
        return toBuilder().backoff(delay, multiplier).build();
    }

    /**
     * @param method The method of a request
     * @return Whether it is sent again after a failure before the headers or a retryable status
     */
    public boolean retriesMethod(String method) {
        return methods.contains(method);
    }

    /**
     * @param status The status of a response
     * @return Whether it is retried, for a request of {@link #methods()}
     */
    public boolean retriesStatus(int status) {
        return statuses.contains(status);
    }

    /**
     * @param predicate The predicate of the failures to retry
     * @return The policy of the delays
     */
    RetryPolicy toPolicy(java.util.function.Predicate<@Nullable Throwable> predicate) {
        return RetryPolicy.builder()
            .maxAttempts(retries)
            .delay(delay)
            .multiplier(multiplier)
            .jitter(jitter)
            .predicate(predicate::test)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GatewayRetry that
            && retries == that.retries
            && Double.compare(multiplier, that.multiplier) == 0
            && Double.compare(jitter, that.jitter) == 0
            && methods.equals(that.methods)
            && statuses.equals(that.statuses)
            && delay.equals(that.delay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retries, methods, statuses, delay, multiplier, jitter);
    }

    @Override
    public String toString() {
        return "Retry=" + retries;
    }

    /**
     * The builder of {@link GatewayRetry}.
     */
    public static final class Builder {
        private final int retries;
        private Set<String> methods = DEFAULT_METHODS;
        private Set<Integer> statuses = DEFAULT_STATUSES;
        private Duration delay = DEFAULT_DELAY;
        private double multiplier = DEFAULT_MULTIPLIER;
        private double jitter;

        private Builder(int retries) {
            if (retries < 1) {
                throw new IllegalArgumentException("A gateway route retries at least once: " + retries);
            }
            this.retries = retries;
        }

        /**
         * @param methods See {@link GatewayRetry#methods()}
         * @return This builder
         */
        public Builder methods(Set<String> methods) {
            Set<String> upper = new LinkedHashSet<>();
            for (String method : Objects.requireNonNull(methods, "methods")) {
                upper.add(method.strip().toUpperCase(Locale.ROOT));
            }
            this.methods = upper;
            return this;
        }

        /**
         * @param statuses See {@link GatewayRetry#statuses()}
         * @return This builder
         */
        public Builder statuses(Set<Integer> statuses) {
            this.statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
            return this;
        }

        /**
         * @param delay      See {@link GatewayRetry#delay()}
         * @param multiplier See {@link GatewayRetry#multiplier()}
         * @return This builder
         */
        public Builder backoff(Duration delay, double multiplier) {
            Objects.requireNonNull(delay, "delay");
            if (delay.isNegative()) {
                throw new IllegalArgumentException("The delay of the retries is negative: " + delay);
            }
            if (multiplier < 0) {
                throw new IllegalArgumentException("The multiplier of the retries is negative: " + multiplier);
            }
            this.delay = delay;
            this.multiplier = multiplier;
            return this;
        }

        /**
         * @param jitter See {@link GatewayRetry#jitter()}
         * @return This builder
         */
        public Builder jitter(double jitter) {
            if (jitter < 0 || jitter > 1) {
                throw new IllegalArgumentException("The jitter of the retries is not between 0 and 1: " + jitter);
            }
            this.jitter = jitter;
            return this;
        }

        /**
         * @return The retries
         */
        public GatewayRetry build() {
            return new GatewayRetry(this);
        }
    }
}
