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
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryRegistry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;

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
    private final @Nullable RetryBudget budget;

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
            // by default only the transport failures, e.g. a connection reset after the request
            // was written; the includes of the policy widen them, its excludes narrow them
            RetryRule transport = PolicyExchangeRetry::isTransportFailure;
            RetryRule failures = transport.and(outcome -> isNotExcluded(policy, outcome))
                .or(policy.includes().isEmpty() ? RetryRule.NEVER : RetryRule.of(policy));
            rule = rule.or(failures.and(outcome -> outcome.kind() == RetryOutcome.Kind.FAILURE));
        }
        this.retriedMethodRule = rule;
        this.otherMethodRule = unprocessed;
        this.budget = configuration.getBudget().isEnabled() ? new RetryBudget(configuration.getBudget()) : null;
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
    public void onSuccess() {
        if (budget != null) {
            budget.onSuccess();
        }
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
    public @Nullable Duration retryDelay(HttpRequest<?> request, int attempt, RetryOutcome outcome, Duration overallDelay) {
        RetryRule rule = configuration.isRetriedMethod(request.getMethod()) ? retriedMethodRule : otherMethodRule;
        if (!rule.shouldRetry(outcome)) {
            return null;
        }
        // every attempt with an outcome that is retried takes a token, the last one included
        if ((budget != null && !budget.onFailure()) || attempt >= maxAttempts) {
            return null;
        }
        Duration maxDelay = policy.maxDelay();
        if (maxDelay != null && overallDelay.compareTo(maxDelay) >= 0) {
            return null;
        }
        Duration delay = policy.nextDelay(attempt);
        Duration retryAfter = outcome.retryAfter();
        if (retryAfter != null && configuration.isRespectRetryAfter()
            && (outcome.code() == HttpStatus.TOO_MANY_REQUESTS.getCode() || outcome.code() == HttpStatus.SERVICE_UNAVAILABLE.getCode())) {
            if (retryAfter.compareTo(configuration.getMaxRetryAfter()) > 0) {
                return null;
            }
            if (retryAfter.compareTo(delay) > 0) {
                delay = retryAfter;
            }
        }
        // the whole wait, this delay included, stays within the maximum delay of the policy
        if (maxDelay != null && overallDelay.plus(delay).compareTo(maxDelay) > 0) {
            return null;
        }
        return delay;
    }

    /**
     * A failure of the transport after the request may have been written: the connection or the
     * stream was closed or reset before the response was complete, or an I/O error. Not a failure
     * that sending the request again would fail with again, e.g. a body over the content length.
     */
    private static boolean isNotExcluded(RetryPolicy policy, RetryOutcome outcome) {
        for (Class<? extends Throwable> excluded : policy.excludes()) {
            if (excluded.isInstance(outcome.failure())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTransportFailure(RetryOutcome outcome) {
        Throwable failure = outcome.failure();
        return failure instanceof ResponseClosedException
            || failure instanceof StreamResetException
            || failure instanceof IOException
            || (failure instanceof HttpClientException && !(failure instanceof ContentLengthExceededException) && failure.getCause() instanceof IOException);
    }
}
