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

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryingExchangeTest {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final HttpRequest<?> REQUEST = HttpRequest.GET("/x");

    @AfterAll
    static void shutdown() {
        SCHEDULER.shutdownNow();
    }

    @Test
    void succeedsAtOnce() throws Exception {
        TestRetry retry = new TestRetry();
        String response = await(exchange(retry, attempt -> ExecutionFlow.just("ok"), true).start());
        assertEquals("ok", response);
        assertEquals(1, retry.successes.get());
        assertTrue(retry.outcomes.isEmpty());
    }

    @Test
    void retriesAFailureThenSucceeds() throws Exception {
        TestRetry retry = new TestRetry();
        List<Integer> numbers = new CopyOnWriteArrayList<>();
        RetryingExchange<String> exchange = new RetryingExchange<>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            number -> {
                numbers.add(number);
                return HttpRequest.GET("/x/" + number);
            },
            attempt -> attempt.number() == 1 ? ExecutionFlow.error(new ResponseClosedException("closed")) : ExecutionFlow.just(attempt.request().getPath()),
            response -> { }, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true);
        assertEquals("/x/2", await(exchange.start()));
        assertEquals(List.of(1, 2), numbers);
        assertEquals(RetryOutcome.Kind.FAILURE, retry.outcomes.get(0).kind());
    }

    @Test
    void waitsBetweenTheAttempts() throws Exception {
        TestRetry retry = new TestRetry();
        retry.decide = outcome -> Duration.ofMillis(100);
        long start = System.nanoTime();
        String response = await(exchange(retry, attempt -> attempt.number() < 3 ? ExecutionFlow.error(new ResponseClosedException("closed")) : ExecutionFlow.just("ok"), true).start());
        assertEquals("ok", response);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 190);
        assertEquals(2, retry.outcomes.size());
    }

    @Test
    void givesUpWithTheLastFailure() {
        TestRetry retry = new TestRetry();
        retry.maxAttempts = 2;
        ResponseClosedException last = new ResponseClosedException("last");
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(exchange(retry,
            attempt -> ExecutionFlow.error(attempt.number() == 2 ? last : new ResponseClosedException("first")), true).start()));
        assertSame(last, e.getCause());
        assertEquals(0, retry.successes.get());
    }

    @Test
    void sendsOnceWithoutRetries() {
        TestRetry retry = new TestRetry();
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(exchange(retry, attempt -> {
            assertNull(attempt.retryStatus(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)));
            return ExecutionFlow.error(new ResponseClosedException("closed"));
        }, false).start()));
        assertInstanceOf(ResponseClosedException.class, e.getCause());
        assertTrue(retry.outcomes.isEmpty());
    }

    @Test
    void timesOutAnAttempt() throws Exception {
        TestRetry retry = new TestRetry();
        retry.attemptTimeout = Duration.ofMillis(100);
        List<String> discarded = new CopyOnWriteArrayList<>();
        DelayedExecutionFlow<String> first = DelayedExecutionFlow.create();
        RetryingExchange<String> exchange = new RetryingExchange<>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            number -> REQUEST, attempt -> attempt.number() == 1 ? first : ExecutionFlow.just("ok"),
            discarded::add, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true);
        assertEquals("ok", await(exchange.start()));
        assertTrue(first.isCancelled());
        assertEquals(RetryOutcome.Kind.TIMEOUT, retry.outcomes.get(0).kind());
        // a response that arrives after the timeout is released
        first.complete("late");
        assertEquals(List.of("late"), discarded);
    }

    @Test
    void anAttemptThatCompletesInTimeCancelsItsTimeout() throws Exception {
        TestRetry retry = new TestRetry();
        retry.attemptTimeout = Duration.ofMillis(50);
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create();
        ExecutionFlow<String> result = exchange(retry, attempt -> flow, true).start();
        flow.complete("ok");
        assertEquals("ok", await(result));
        Thread.sleep(100);
        assertTrue(retry.outcomes.isEmpty(), "no timeout");
    }

    @Test
    void cancellingTheExchangeCancelsTheWaitForTheNextAttempt() throws Exception {
        TestRetry retry = new TestRetry();
        retry.decide = outcome -> Duration.ofSeconds(10);
        AtomicInteger attempts = new AtomicInteger();
        ExecutionFlow<String> result = exchange(retry, attempt -> {
            attempts.incrementAndGet();
            return ExecutionFlow.error(new ResponseClosedException("closed"));
        }, true).start();
        result.cancel();
        Thread.sleep(100);
        assertEquals(1, attempts.get());
    }

    @Test
    void cancellingTheExchangeCancelsTheAttemptAndReleasesItsResponse() {
        TestRetry retry = new TestRetry();
        List<String> discarded = new CopyOnWriteArrayList<>();
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create();
        RetryingExchange<String> exchange = new RetryingExchange<>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            number -> REQUEST, attempt -> flow, discarded::add, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true);
        ExecutionFlow<String> result = exchange.start();
        result.cancel();
        assertTrue(flow.isCancelled());
        flow.complete("late");
        assertEquals(List.of("late"), discarded);
        // a failure after the cancel is not retried
        assertTrue(retry.outcomes.isEmpty());
    }

    @Test
    void retriesAStatusDecidedWhenTheHeadersArrive() throws Exception {
        TestRetry retry = new TestRetry();
        RecordingLoadBalancer loadBalancer = new RecordingLoadBalancer();
        List<Set<URI>> excluded = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        String response = await(exchange(retry, attempt -> {
            excluded.add(attempt.excluded());
            attempt.selected(new LoadBalancerSelection(loadBalancer, ServiceInstance.of("svc", URI.create("http://host" + attempt.number()))));
            if (attempt.number() == 1) {
                HttpClientException retried = attempt.retryStatus(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "0"));
                assertNotNull(retried);
                failures.add(retried);
                return ExecutionFlow.error(retried);
            }
            return ExecutionFlow.just("ok");
        }, true).start());
        assertEquals("ok", response);
        assertEquals(List.of(Set.of(), Set.of(URI.create("http://host1"))), excluded, "the second attempt leaves out the first instance");
        assertEquals(List.of(LoadBalancer.Outcome.SERVER_ERROR), loadBalancer.outcomes);
        HttpClientResponseException retried = assertInstanceOf(HttpClientResponseException.class, failures.get(0));
        assertEquals(503, retried.code());
        assertEquals("0", retried.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER));
        assertEquals(RetryOutcome.result(503, Duration.ZERO), retry.outcomes.get(0));
    }

    @Test
    void aStatusThatIsNotRetriedIsNotDecidedTwice() {
        TestRetry retry = new TestRetry();
        retry.decide = outcome -> null;
        assertThrows(ExecutionException.class, () -> await(exchange(retry, attempt -> {
            assertNull(attempt.retryStatus(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)));
            return ExecutionFlow.error(new HttpClientResponseException("Service Unavailable", HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)));
        }, true).start()));
        assertEquals(1, retry.outcomes.size(), "the failure of the decided status is not decided again");
    }

    @Test
    void classifiesTheFailures() {
        TestRetry retry = new TestRetry();
        retry.decide = outcome -> null;
        List<Throwable> failures = List.of(
            new NoAvailableServiceException("svc"),
            new UnprocessedRequestException(UnprocessedRequestException.Reason.CONNECT, "refused", null),
            new ReadTimeoutException(),
            new HttpClientResponseException("Too Many Requests", HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3")),
            new IllegalStateException("other")
        );
        for (Throwable failure : failures) {
            assertThrows(ExecutionException.class, () -> await(exchange(retry, attempt -> ExecutionFlow.error(failure), true).start()));
        }
        assertEquals(List.of(RetryOutcome.Kind.FAILURE, RetryOutcome.Kind.UNPROCESSED, RetryOutcome.Kind.TIMEOUT, RetryOutcome.Kind.RESULT, RetryOutcome.Kind.FAILURE),
            retry.outcomes.stream().map(RetryOutcome::kind).toList());
        assertEquals(RetryOutcome.result(429, Duration.ofSeconds(3)), retry.outcomes.get(3));
    }

    @Test
    void aFailureOfTheRetryFailsTheExchange() {
        TestRetry retry = new TestRetry();
        retry.decide = outcome -> {
            throw new IllegalStateException("broken");
        };
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(exchange(retry, attempt -> ExecutionFlow.error(new ResponseClosedException("closed")), true).start()));
        assertInstanceOf(ResponseClosedException.class, e.getCause());
        assertEquals("broken", e.getCause().getSuppressed()[0].getMessage());
    }

    @Test
    void aFailingSenderFailsTheAttempt() {
        TestRetry retry = new TestRetry();
        retry.maxAttempts = 1;
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(exchange(retry, attempt -> {
            throw new IllegalStateException("sender");
        }, true).start()));
        assertEquals("sender", e.getCause().getMessage());
    }

    @Test
    void aFailureToPrepareARequestFailsTheExchange() {
        TestRetry retry = new TestRetry();
        IntFunction<HttpRequest<?>> firstFails = number -> {
            throw new IllegalStateException("first");
        };
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(new RetryingExchange<String>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            firstFails, attempt -> ExecutionFlow.just("ok"), response -> { }, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true).start()));
        assertEquals("first", e.getCause().getMessage());
        IntFunction<HttpRequest<?>> secondFails = number -> {
            if (number == 2) {
                throw new IllegalStateException("second");
            }
            return REQUEST;
        };
        e = assertThrows(ExecutionException.class, () -> await(new RetryingExchange<String>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            secondFails, attempt -> ExecutionFlow.error(new ResponseClosedException("closed")), response -> { }, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true).start()));
        assertEquals("second", e.getCause().getMessage());
    }

    @Test
    void readsRetryAfter() {
        assertNull(RetryingExchange.retryAfter(headers(null)));
        assertEquals(Duration.ofSeconds(5), RetryingExchange.retryAfter(headers("5")));
        assertNull(RetryingExchange.retryAfter(headers("-1")));
        assertNull(RetryingExchange.retryAfter(headers("soon")));
        String future = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        Duration untilFuture = RetryingExchange.retryAfter(headers(future));
        assertNotNull(untilFuture);
        assertTrue(untilFuture.compareTo(Duration.ofSeconds(20)) > 0 && untilFuture.compareTo(Duration.ofSeconds(31)) <= 0, untilFuture.toString());
        String past = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(30));
        assertEquals(Duration.ZERO, RetryingExchange.retryAfter(headers(past)));
    }

    @Test
    void anAttemptKnowsItsRequestAndNumber() throws Exception {
        TestRetry retry = new TestRetry();
        HttpRequest<?> request = HttpRequest.GET("/attempt");
        String response = await(new RetryingExchange<String>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER,
            number -> request, attempt -> {
                attempt.selected(null);
                return ExecutionFlow.just(attempt.number() + " " + attempt.request().getPath());
            }, r -> { }, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), true).start());
        assertEquals("1 /attempt", response);
    }

    private static HttpHeaders headers(@Nullable String retryAfter) {
        var response = HttpResponse.ok();
        if (retryAfter != null) {
            response.header(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return response.getHeaders();
    }

    private static RetryingExchange<String> exchange(TestRetry retry, RetryingExchange.AttemptSender<String> sender, boolean retries) {
        return new RetryingExchange<>(retry, REQUEST, PropagatedContext.getOrEmpty(), SCHEDULER, number -> REQUEST, sender,
            response -> { }, ReadTimeoutException::new, LoggerFactory.getLogger(RetryingExchangeTest.class), retries);
    }

    private static <T> T await(ExecutionFlow<T> flow) throws Exception {
        return flow.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * A retry that retries every outcome as it is told to.
     */
    static final class TestRetry implements ExchangeRetry {
        final List<RetryOutcome> outcomes = new CopyOnWriteArrayList<>();
        final AtomicInteger successes = new AtomicInteger();
        volatile int maxAttempts = 3;
        volatile @Nullable Duration attemptTimeout;
        volatile Function<RetryOutcome, @Nullable Duration> decide = outcome -> Duration.ZERO;

        @Override
        public int maxAttempts() {
            return maxAttempts;
        }

        @Override
        public @Nullable Duration attemptTimeout() {
            return attemptTimeout;
        }

        @Override
        public void onSuccess() {
            successes.incrementAndGet();
        }

        @Override
        public @Nullable Duration retryDelay(HttpRequest<?> request, int attempt, RetryOutcome outcome, Duration overallDelay) {
            outcomes.add(outcome);
            return attempt >= maxAttempts ? null : decide.apply(outcome);
        }
    }

    /**
     * Records the outcomes reported to it.
     */
    static final class RecordingLoadBalancer implements LoadBalancer {
        final List<Outcome> outcomes = new CopyOnWriteArrayList<>();

        @Override
        public org.reactivestreams.Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Publishers.empty();
        }

        @Override
        public void report(ServiceInstance serviceInstance, Outcome outcome) {
            outcomes.add(outcome);
        }
    }
}
