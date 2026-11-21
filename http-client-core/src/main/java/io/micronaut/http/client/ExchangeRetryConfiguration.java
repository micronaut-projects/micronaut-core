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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The retry of the exchanges of a service client, decided from the outcome of each attempt: a
 * response status, a failure, a request that was never processed, or an attempt that timed out.
 * Each attempt goes through the client filters, selects an instance of the service that was not
 * tried yet, and is reported to the load balancer. Disabled by default.
 *
 * <p>The delays between the attempts, the number of retries, and the failures that are retried
 * come from a named retry policy of {@code micronaut-retry}, see {@link #getPolicy()}.
 *
 * <p>The retry applies to the exchanges whose response body is read whole ({@code exchange},
 * {@code retrieve}, declarative clients); streamed and raw exchanges are sent once. A request is
 * retried only if its body can be sent again: a body held in memory (an object, a string,
 * bytes) can, a body read once (a {@code Publisher}, an {@code InputStream}, a
 * {@code ByteBody}, a multipart body with an {@code InputStream} part, a reference counted
 * buffer, the relayed bytes of a server request) cannot.
 *
 * @since 5.3.0
 */
@Experimental
public class ExchangeRetryConfiguration {

    /**
     * The prefix of the configuration, under the prefix of a service.
     */
    public static final String PREFIX = "retry";

    /**
     * The default maximum number of attempts.
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 10;

    /**
     * The default statuses that are retried.
     */
    public static final List<Integer> DEFAULT_STATUSES = List.of(502, 503, 504);

    /**
     * The default longest {@code Retry-After} that is waited for.
     */
    public static final Duration DEFAULT_MAX_RETRY_AFTER = Duration.ofSeconds(30);

    private boolean enabled;
    @Nullable
    private String policy;
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private List<Integer> statuses = DEFAULT_STATUSES;
    private List<String> statusClasses = List.of();
    @Nullable
    private List<HttpMethod> methods;
    private boolean retryOnUnprocessed = true;
    private boolean retryOnTimeout = true;
    private boolean retryOnFailure = true;
    @Nullable
    private Duration attemptTimeout;
    private boolean respectRetryAfter = true;
    private Duration maxRetryAfter = DEFAULT_MAX_RETRY_AFTER;
    private final BudgetConfiguration budget;

    /**
     * A configuration with the default retry budget.
     */
    public ExchangeRetryConfiguration() {
        this(new BudgetConfiguration());
    }

    /**
     * @param budget The retry budget
     */
    protected ExchangeRetryConfiguration(BudgetConfiguration budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /**
     * @return Whether the exchanges of the service are retried, default {@code false}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether the exchanges of the service are retried, default {@code false}
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The name of the retry policy, configured under {@code micronaut.retry.policies.<name>},
     * that gives the delay between attempts ({@code delay}, {@code multiplier}, {@code jitter},
     * and {@code max-delay}, the longest wait in all, which a retry does not exceed), the
     * number of retries ({@code attempts}) and the failures that are retried
     * ({@code includes}, {@code excludes}). Without a name, the defaults of a retry
     * policy apply: three retries, one second apart.
     *
     * @return The name of the retry policy, or {@code null}
     */
    public @Nullable String getPolicy() {
        return policy;
    }

    /**
     * @param policy The name of the retry policy, see {@link #getPolicy()}
     */
    public void setPolicy(@Nullable String policy) {
        this.policy = policy;
    }

    /**
     * The maximum number of attempts of an exchange, the first one included, whatever the
     * policy allows. Default {@value #DEFAULT_MAX_ATTEMPTS}.
     *
     * @return The maximum number of attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * @param maxAttempts The maximum number of attempts, at least 1, see {@link #getMaxAttempts()}
     */
    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("max-attempts must be greater than or equal to 1");
        }
        this.maxAttempts = maxAttempts;
    }

    /**
     * The response statuses that are retried. Default {@code 502}, {@code 503}, {@code 504}.
     *
     * @return The statuses
     */
    public List<Integer> getStatuses() {
        return statuses;
    }

    /**
     * @param statuses The response statuses that are retried
     */
    public void setStatuses(List<Integer> statuses) {
        this.statuses = List.copyOf(Objects.requireNonNull(statuses, "statuses"));
    }

    /**
     * The classes of the response statuses that are retried, e.g. {@code 5xx}. Default none.
     *
     * @return The status classes
     */
    public List<String> getStatusClasses() {
        return statusClasses;
    }

    /**
     * @param statusClasses The classes of the response statuses that are retried, e.g.
     *                      {@code 5xx}
     */
    public void setStatusClasses(List<String> statusClasses) {
        for (String statusClass : statusClasses) {
            statusClassDigit(statusClass);
        }
        this.statusClasses = List.copyOf(statusClasses);
    }

    /**
     * The request methods whose exchanges are retried on a status, a timeout or a failure.
     * Default {@code null}: the idempotent methods. A request that was never processed is
     * retried whatever its method, see {@link #isRetryOnUnprocessed()}.
     *
     * @return The methods, or {@code null} for the idempotent methods
     */
    public @Nullable List<HttpMethod> getMethods() {
        return methods;
    }

    /**
     * @param methods The request methods that are retried, see {@link #getMethods()}
     */
    public void setMethods(@Nullable List<HttpMethod> methods) {
        this.methods = methods == null ? null : List.copyOf(methods);
    }

    /**
     * Whether a request that was never processed by the server is retried, whatever its method:
     * the connection could not be opened, the server refused the stream, or the connection was
     * closed before the request was written. Default {@code true}.
     *
     * @return Whether an unprocessed request is retried
     */
    public boolean isRetryOnUnprocessed() {
        return retryOnUnprocessed;
    }

    /**
     * @param retryOnUnprocessed Whether an unprocessed request is retried, see
     *                           {@link #isRetryOnUnprocessed()}
     */
    public void setRetryOnUnprocessed(boolean retryOnUnprocessed) {
        this.retryOnUnprocessed = retryOnUnprocessed;
    }

    /**
     * Whether an attempt that timed out is retried, if its method is retried. Default
     * {@code true}.
     *
     * @return Whether a timeout is retried
     */
    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    /**
     * @param retryOnTimeout Whether a timeout is retried
     */
    public void setRetryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
    }

    /**
     * Whether an attempt that failed in the transport after the request may have been sent, e.g.
     * the connection or the stream was closed or reset before the response was complete, is
     * retried, if its method is retried. Other failures are only retried when the
     * {@code includes} of the policy name them; its {@code excludes} leave failures out.
     * Default {@code true}.
     *
     * @return Whether a failure is retried
     */
    public boolean isRetryOnFailure() {
        return retryOnFailure;
    }

    /**
     * @param retryOnFailure Whether a failure is retried
     */
    public void setRetryOnFailure(boolean retryOnFailure) {
        this.retryOnFailure = retryOnFailure;
    }

    /**
     * The timeout of each attempt, until its whole response arrives, also for an exchange that
     * is sent once. The request timeout still applies to the whole exchange, every attempt
     * included. Default {@code null}, none.
     *
     * @return The timeout of an attempt, or {@code null}
     */
    public @Nullable Duration getAttemptTimeout() {
        return attemptTimeout;
    }

    /**
     * @param attemptTimeout The timeout of an attempt, see {@link #getAttemptTimeout()}
     */
    public void setAttemptTimeout(@Nullable Duration attemptTimeout) {
        this.attemptTimeout = attemptTimeout;
    }

    /**
     * Whether the {@code Retry-After} header of a {@code 429} or {@code 503} response that is
     * retried sets the least delay before the retry. A response that asks to wait longer than
     * {@link #getMaxRetryAfter()} is not retried. Default {@code true}.
     *
     * @return Whether {@code Retry-After} is respected
     */
    public boolean isRespectRetryAfter() {
        return respectRetryAfter;
    }

    /**
     * @param respectRetryAfter Whether {@code Retry-After} is respected
     */
    public void setRespectRetryAfter(boolean respectRetryAfter) {
        this.respectRetryAfter = respectRetryAfter;
    }

    /**
     * @return The longest {@code Retry-After} that is waited for, default 30 seconds
     */
    public Duration getMaxRetryAfter() {
        return maxRetryAfter;
    }

    /**
     * @param maxRetryAfter The longest {@code Retry-After} that is waited for
     */
    public void setMaxRetryAfter(Duration maxRetryAfter) {
        this.maxRetryAfter = Objects.requireNonNull(maxRetryAfter, "maxRetryAfter");
    }

    /**
     * The retry budget of the service, shared by all its concurrent exchanges, under
     * {@code retry.budget}: it stops the retries while most attempts fail, so that the retries do
     * not multiply the load of a service that is down.
     *
     * @return The retry budget
     */
    public BudgetConfiguration getBudget() {
        return budget;
    }

    /**
     * @param status A response status
     * @return Whether the status is retried, see {@link #getStatuses()} and
     * {@link #getStatusClasses()}
     */
    public boolean isRetriedStatus(int status) {
        if (statuses.contains(status)) {
            return true;
        }
        for (String statusClass : statusClasses) {
            if (status / 100 == statusClassDigit(statusClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param method A request method
     * @return Whether an exchange of the method is retried on a status, a timeout or a failure
     */
    public boolean isRetriedMethod(HttpMethod method) {
        return methods == null ? method.isIdempotent() : methods.contains(method);
    }

    private static int statusClassDigit(String statusClass) {
        String normalized = statusClass.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() != 3 || !normalized.endsWith("xx") || normalized.charAt(0) < '1' || normalized.charAt(0) > '5') {
            throw new IllegalArgumentException("A status class must be one of 1xx, 2xx, 3xx, 4xx or 5xx, got: " + statusClass);
        }
        return normalized.charAt(0) - '0';
    }

    /**
     * The retry budget of a service, a token bucket shared by all its exchanges, as the retry
     * throttling of gRPC: the bucket starts full with {@link #getMaxTokens()} tokens, every attempt
     * whose outcome is retried takes one, every exchange that succeeds puts
     * {@link #getTokenRatio()} back, and a retry is only made while more than half of the tokens
     * are left.
     */
    public static class BudgetConfiguration {

        /**
         * The default number of tokens.
         */
        public static final int DEFAULT_MAX_TOKENS = 10;

        /**
         * The default number of tokens a success puts back.
         */
        public static final double DEFAULT_TOKEN_RATIO = 0.1;

        private boolean enabled = true;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private double tokenRatio = DEFAULT_TOKEN_RATIO;

        /**
         * @return Whether the retries are limited by the budget, default {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether the retries are limited by the budget
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return The number of tokens of a full bucket, default {@value #DEFAULT_MAX_TOKENS}
         */
        public int getMaxTokens() {
            return maxTokens;
        }

        /**
         * @param maxTokens The number of tokens of a full bucket, at least 1
         */
        public void setMaxTokens(int maxTokens) {
            if (maxTokens < 1) {
                throw new IllegalArgumentException("max-tokens must be greater than or equal to 1");
            }
            this.maxTokens = maxTokens;
        }

        /**
         * @return The number of tokens an exchange that succeeds puts back, default
         * {@value #DEFAULT_TOKEN_RATIO}
         */
        public double getTokenRatio() {
            return tokenRatio;
        }

        /**
         * @param tokenRatio The number of tokens an exchange that succeeds puts back, above 0
         */
        public void setTokenRatio(double tokenRatio) {
            if (!(tokenRatio > 0)) {
                throw new IllegalArgumentException("token-ratio must be greater than 0");
            }
            this.tokenRatio = tokenRatio;
        }
    }
}
