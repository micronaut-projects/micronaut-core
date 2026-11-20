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
package io.micronaut.http.client.internal;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.ExchangeRetryConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.retry.RetryOutcome;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryRegistry;
import io.micronaut.retry.RetryRule;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * The {@link ExchangeRetry} of a {@link RetryPolicy} of {@code micronaut-retry}: the HTTP
 * conditions of the configuration become a {@link RetryRule}, the delays, the number of retries
 * and the failures that are retried come from the policy of the configured name.
 *
 * @since 5.3.0
 */
@Internal
public final class PolicyExchangeRetry implements ExchangeRetry {
    private final ExchangeRetryConfiguration configuration;
    private final RetryPolicy policy;
    private final int maxAttempts;
    /**
     * The rule of a request whose method is retried.
     */
    private final RetryRule retriedMethodRule;
    /**
     * The rule of a request whose method is not retried: only a request that was not
     * processed.
     */
    private final RetryRule otherMethodRule;

    private PolicyExchangeRetry(ExchangeRetryConfiguration configuration, RetryPolicy policy) {
        this.configuration = configuration;
        this.policy = policy;
        // the policy counts the retries, after the first attempt
        this.maxAttempts = (int) Math.min(configuration.getMaxAttempts(), policy.maxAttempts() + 1L);
        RetryRule unprocessed = configuration.isRetryOnUnprocessed() ? RetryRule.onUnprocessed() : RetryRule.NEVER;
        RetryRule rule = RetryRule.onCode(configuration::isRetriedStatus).or(unprocessed);
        if (configuration.isRetryOnTimeout()) {
            rule = rule.or(RetryRule.onTimeout());
        }
        if (configuration.isRetryOnFailure()) {
            rule = rule.or(RetryRule.of(policy).and(outcome -> outcome.kind() == RetryOutcome.Kind.FAILURE));
        }
        this.retriedMethodRule = rule;
        this.otherMethodRule = unprocessed;
    }

    /**
     * @param serviceId     The id of the service, for the messages
     * @param configuration The configuration
     * @param retryRegistry The registry of the named policies, or {@code null} without a context
     * @return The retry
     * @throws ConfigurationException if the configured policy does not exist or is invalid
     */
    public static ExchangeRetry create(String serviceId, ExchangeRetryConfiguration configuration, @Nullable RetryRegistry retryRegistry) {
        String name = configuration.getPolicy();
        RetryPolicy policy;
        try {
            if (name == null) {
                policy = RetryPolicy.builder().build();
            } else if (retryRegistry == null) {
                throw new IllegalArgumentException("No retry policy named [" + name + "] is configured");
            } else {
                policy = retryRegistry.getPolicy(name);
            }
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid retry of " + ServiceHttpClientConfiguration.PREFIX + "." + serviceId + "."
                + ExchangeRetryConfiguration.PREFIX + ": " + e.getMessage(), e);
        }
        return new PolicyExchangeRetry(configuration, policy);
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public @Nullable Duration attemptTimeout() {
        return configuration.getAttemptTimeout();
    }

    @Override
    public @Nullable String attemptHeader() {
        return configuration.getAttemptHeader();
    }

    @Override
    public @Nullable Duration retryDelay(HttpRequest<?> request, int attempt, Outcome outcome, Duration overallDelay) {
        if (attempt >= maxAttempts) {
            return null;
        }
        Duration maxDelay = policy.maxDelay();
        if (maxDelay != null && overallDelay.compareTo(maxDelay) >= 0) {
            return null;
        }
        RetryRule rule = configuration.isRetriedMethod(request.getMethod()) ? retriedMethodRule : otherMethodRule;
        if (!rule.shouldRetry(toRetryOutcome(outcome))) {
            return null;
        }
        Duration delay = policy.nextDelay(attempt);
        Duration retryAfter = outcome.retryAfter();
        if (retryAfter != null && configuration.isRespectRetryAfter()
            && (outcome.status() == HttpStatus.TOO_MANY_REQUESTS.getCode() || outcome.status() == HttpStatus.SERVICE_UNAVAILABLE.getCode())) {
            if (retryAfter.compareTo(configuration.getMaxRetryAfter()) > 0) {
                return null;
            }
            if (retryAfter.compareTo(delay) > 0) {
                delay = retryAfter;
            }
        }
        return delay;
    }

    private static RetryOutcome toRetryOutcome(Outcome outcome) {
        if (outcome.kind() == Kind.RESPONSE) {
            return RetryOutcome.result(outcome.status());
        }
        Throwable failure = Objects.requireNonNull(outcome.failure(), "failure");
        return switch (outcome.kind()) {
            case UNPROCESSED -> RetryOutcome.unprocessed(failure);
            case TIMEOUT -> RetryOutcome.timeout(failure);
            default -> RetryOutcome.failure(failure);
        };
    }
}
