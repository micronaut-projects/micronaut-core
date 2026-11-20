package io.micronaut.http.server;

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.RequestTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDeadlineTest {

    private static RequestDeadline start(HttpRequest<?> request, Duration timeout) {
        return RequestDeadline.start(request, timeout, PropagatedContext.empty(), () -> { });
    }

    private static void awaitExpiry(RequestDeadline deadline) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!deadline.isExpired() && System.nanoTime() < until) {
            Thread.sleep(5);
        }
        assertTrue(deadline.isExpired(), "the deadline expired");
    }

    private static Throwable failureOf(ExecutionFlow<HttpResponse<?>> flow) throws InterruptedException {
        CompletableFuture<HttpResponse<?>> future = flow.toCompletableFuture();
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("the flow succeeded");
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("the flow did not complete", e);
        }
    }

    @Test
    void aRouteBoundAfterTheExpiryFailsWithTheTimeout() throws Exception {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofMillis(1));
        awaitExpiry(deadline);
        // a pending delayed flow accepts no completion listener once it was cancelled: binding
        // must listen before it cancels
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(route, response -> { });
        assertInstanceOf(RequestTimeoutException.class, failureOf(bound));
        assertTrue(route.isCancelled(), "the route was cancelled");
    }

    @Test
    void aResponseAfterTheExpiryIsDiscarded() throws Exception {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofMillis(50));
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        AtomicReference<HttpResponse<?>> discarded = new AtomicReference<>();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(route, discarded::set);
        assertInstanceOf(RequestTimeoutException.class, failureOf(bound));
        HttpResponse<?> late = HttpResponse.ok();
        route.complete(late);
        assertSame(late, discarded.get());
    }

    @Test
    void aResponseInTimeEndsTheDeadline() throws Exception {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofSeconds(5));
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(route, response -> { });
        HttpResponse<?> response = HttpResponse.ok();
        route.complete(response);
        assertSame(response, bound.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertTrue(deadline.remaining().isEmpty(), "the deadline ended");
        assertFalse(deadline.extend(Duration.ofSeconds(1)), "an ended deadline is not extended");
    }

    @Test
    void anAbandonedRequestStopsTheDeadlineAndCancelsTheRoute() throws Exception {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofMillis(100));
        CountDownLatch hookRemoved = new CountDownLatch(1);
        deadline.onEnd(hookRemoved::countDown);
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        deadline.bind(route, response -> { });
        deadline.abandon();
        assertTrue(route.isCancelled(), "the route was cancelled");
        assertEquals(0, hookRemoved.getCount(), "the end hook ran");
        Thread.sleep(200);
        assertFalse(deadline.isExpired(), "an abandoned deadline does not expire");
    }

    @Test
    void theEndHookRunsOnceWhenTheDeadlineIsCleared() {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofSeconds(5));
        AtomicBoolean ran = new AtomicBoolean();
        deadline.onEnd(() -> assertFalse(ran.getAndSet(true), "the hook ran twice"));
        assertTrue(deadline.clear());
        assertTrue(deadline.clear());
        assertTrue(ran.get());
    }
}
