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
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.ExchangeRetryConfiguration;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyExchangeRetryTest {

    private static final Duration DELAY = Duration.ofMillis(10);
    private static final HttpRequest<?> GET = HttpRequest.GET("/x");
    private static final HttpRequest<?> POST = HttpRequest.POST("/x", "body");

    @Test
    void retriesTheConfiguredStatusesOfTheRetriedMethods() {
        ExchangeRetry retry = retry(configuration(), policy().build());
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(400), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(500), Duration.ZERO));
        assertNull(retry.retryDelay(POST, 1, RetryOutcome.result(503), Duration.ZERO), "POST is not idempotent");
    }

    @Test
    void retriesAnUnprocessedRequestWhateverItsMethod() {
        ExchangeRetry retry = retry(configuration(), policy().build());
        RetryOutcome unprocessed = RetryOutcome.unprocessed(new UnprocessedRequestException(UnprocessedRequestException.Reason.CONNECT, "refused", null));
        assertEquals(DELAY, retry.retryDelay(POST, 1, unprocessed, Duration.ZERO));
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setRetryOnUnprocessed(false);
        assertNull(retry(configuration, policy().build()).retryDelay(POST, 1, unprocessed, Duration.ZERO));
    }

    @Test
    void retriesATimeout() {
        RetryOutcome timeout = RetryOutcome.timeout(new ReadTimeoutException());
        assertEquals(DELAY, retry(configuration(), policy().build()).retryDelay(GET, 1, timeout, Duration.ZERO));
        assertNull(retry(configuration(), policy().build()).retryDelay(POST, 1, timeout, Duration.ZERO));
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setRetryOnTimeout(false);
        assertNull(retry(configuration, policy().build()).retryDelay(GET, 1, timeout, Duration.ZERO));
    }

    @Test
    void retriesTheTransportFailuresByDefault() {
        ExchangeRetry retry = retry(configuration(), policy().build());
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.failure(new ResponseClosedException("closed")), Duration.ZERO));
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.failure(new StreamResetException(2)), Duration.ZERO));
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.failure(new HttpClientException("Error sending request", new IOException("reset"))), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.failure(new ContentLengthExceededException(10, 20)), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.failure(new IllegalStateException("filter")), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.failure(new NoAvailableServiceException("svc")), Duration.ZERO));
        assertNull(retry.retryDelay(POST, 1, RetryOutcome.failure(new ResponseClosedException("closed")), Duration.ZERO));
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setRetryOnFailure(false);
        assertNull(retry(configuration, policy().build()).retryDelay(GET, 1, RetryOutcome.failure(new ResponseClosedException("closed")), Duration.ZERO));
    }

    @Test
    void theIncludesOfThePolicyWidenTheFailuresAndItsExcludesNarrowThem() {
        ExchangeRetry including = retry(configuration(), policy().includes(IllegalStateException.class).build());
        assertEquals(DELAY, including.retryDelay(GET, 1, RetryOutcome.failure(new IllegalStateException("filter")), Duration.ZERO));
        assertEquals(DELAY, including.retryDelay(GET, 1, RetryOutcome.failure(new ResponseClosedException("closed")), Duration.ZERO));
        ExchangeRetry excluding = retry(configuration(), policy().excludes(ResponseClosedException.class).build());
        assertNull(excluding.retryDelay(GET, 1, RetryOutcome.failure(new ResponseClosedException("closed")), Duration.ZERO));
        assertEquals(DELAY, excluding.retryDelay(GET, 1, RetryOutcome.failure(new StreamResetException(2)), Duration.ZERO));
    }

    @Test
    void capsTheAttempts() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setMaxAttempts(2);
        ExchangeRetry retry = retry(configuration, policy().maxAttempts(5).build());
        assertEquals(2, retry.maxAttempts());
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 2, RetryOutcome.result(503), Duration.ZERO));
        assertEquals(3, retry(configuration(), policy().maxAttempts(2).build()).maxAttempts(), "the policy counts the retries");
    }

    @Test
    void staysWithinTheMaxDelayOfThePolicy() {
        ExchangeRetry retry = retry(configuration(), policy().maxDelay(Duration.ofMillis(25)).build());
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ofMillis(10)));
        assertNull(retry.retryDelay(GET, 2, RetryOutcome.result(503), Duration.ofMillis(20)), "the delay would exceed the max delay");
        assertNull(retry.retryDelay(GET, 3, RetryOutcome.result(503), Duration.ofMillis(25)), "the max delay was reached");
    }

    @Test
    void respectsRetryAfter() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setStatuses(java.util.List.of(429, 500, 503));
        configuration.setMaxRetryAfter(Duration.ofSeconds(5));
        ExchangeRetry retry = retry(configuration, policy().build());
        assertEquals(Duration.ofSeconds(2), retry.retryDelay(GET, 1, RetryOutcome.result(503, Duration.ofSeconds(2)), Duration.ZERO));
        assertEquals(Duration.ofSeconds(2), retry.retryDelay(GET, 1, RetryOutcome.result(429, Duration.ofSeconds(2)), Duration.ZERO));
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503, Duration.ofMillis(1)), Duration.ZERO), "the policy delay is longer");
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(500, Duration.ofSeconds(2)), Duration.ZERO), "only 429 and 503");
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(503, Duration.ofSeconds(6)), Duration.ZERO), "longer than the maximum");
        configuration.setRespectRetryAfter(false);
        assertEquals(DELAY, retry(configuration, policy().build()).retryDelay(GET, 1, RetryOutcome.result(503, Duration.ofSeconds(6)), Duration.ZERO));
    }

    @Test
    void theBudgetStopsTheRetries() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.getBudget().setEnabled(true);
        configuration.getBudget().setMaxTokens(4);
        configuration.getBudget().setTokenRatio(1);
        ExchangeRetry retry = retry(configuration, policy().build());
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        retry.onSuccess();
        retry.onSuccess();
        assertEquals(DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        // an outcome that is not retried takes no token
        for (int i = 0; i < 10; i++) {
            assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(400), Duration.ZERO));
        }
        configuration.getBudget().setEnabled(false);
        ExchangeRetry unlimited = retry(configuration, policy().build());
        for (int i = 0; i < 10; i++) {
            assertEquals(DELAY, unlimited.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
        }
        unlimited.onSuccess();
    }

    @Test
    void theConfiguredMethodsAreRetried() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setMethods(java.util.List.of(io.micronaut.http.HttpMethod.POST));
        ExchangeRetry retry = retry(configuration, policy().build());
        assertEquals(DELAY, retry.retryDelay(POST, 1, RetryOutcome.result(503), Duration.ZERO));
        assertNull(retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
    }

    @Test
    void withoutPolicyTheDefaultsOfAPolicyApply() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setPolicy(null);
        configuration.setAttemptTimeout(Duration.ofSeconds(1));
        ExchangeRetry retry = PolicyExchangeRetry.create("svc", configuration, null);
        assertEquals(RetryPolicy.DEFAULT_MAX_ATTEMPTS + 1, retry.maxAttempts());
        assertEquals(Duration.ofSeconds(1), retry.attemptTimeout());
        assertEquals(RetryPolicy.DEFAULT_DELAY, retry.retryDelay(GET, 1, RetryOutcome.result(503), Duration.ZERO));
    }

    @Test
    void anUnknownPolicyIsAConfigurationError() {
        ExchangeRetryConfiguration configuration = configuration();
        configuration.setPolicy("missing");
        assertThrows(ConfigurationException.class, () -> PolicyExchangeRetry.create("svc", configuration, null));
        assertThrows(ConfigurationException.class, () -> PolicyExchangeRetry.create("svc", configuration, registry(Map.of())));
    }

    private static ExchangeRetryConfiguration configuration() {
        ExchangeRetryConfiguration configuration = new ExchangeRetryConfiguration();
        configuration.setEnabled(true);
        configuration.setPolicy("test");
        // the budget has its own test
        configuration.getBudget().setEnabled(false);
        return configuration;
    }

    private static RetryPolicy.Builder policy() {
        return RetryPolicy.builder().delay(DELAY).maxAttempts(5);
    }

    private static ExchangeRetry retry(ExchangeRetryConfiguration configuration, RetryPolicy policy) {
        return PolicyExchangeRetry.create("svc", configuration, registry(Map.of("test", policy)));
    }

    static RetryRegistry registry(Map<String, RetryPolicy> policies) {
        return new RetryRegistry() {
            @Override
            public Optional<RetryPolicy> findPolicy(String name) {
                return Optional.ofNullable(policies.get(name));
            }

            @Override
            public RetryOperations retry(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<String> getNames() {
                return policies.keySet();
            }
        };
    }
}
