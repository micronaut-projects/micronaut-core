package io.micronaut.http.server;

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.exceptions.RequestTimeoutException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
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
        return RequestDeadline.start(request, timeout, PropagatedContext.empty(), () -> { }, null);
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
        assertFalse(deadline.isExpired(), "a deadline the route met does not expire");
    }

    @Test
    void anAbandonedRequestStopsTheDeadlineAndLetsTheRouteAnswer() throws Exception {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofMillis(100));
        CountDownLatch hookRemoved = new CountDownLatch(1);
        deadline.onEnd(hookRemoved::countDown);
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(route, response -> { });
        deadline.abandon();
        assertFalse(route.isCancelled(), "the route runs on");
        assertEquals(0, hookRemoved.getCount(), "the end hook ran");
        Thread.sleep(200);
        assertFalse(deadline.isExpired(), "an abandoned deadline does not expire");
        // the response of the route completes the request, which releases it
        HttpResponse<?> response = HttpResponse.ok();
        route.complete(response);
        assertSame(response, bound.toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void anAbandonedReactiveRouteAnswersTheRequest() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        Sinks.One<HttpResponse<?>> sink = Sinks.one();
        CompletableFuture<HttpResponse<?>> answered = deadline.bind(ReactiveExecutionFlow.fromPublisher(sink.asMono()), r -> { })
            .toCompletableFuture();
        deadline.abandon();
        HttpResponse<?> response = HttpResponse.ok();
        sink.tryEmitValue(response);
        assertSame(response, answered.get(5, TimeUnit.SECONDS));
    }

    @Test
    void theDeadlineRunsOnTheSchedulerOfTheServer() throws Exception {
        java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicReference<String> expiredOn = new AtomicReference<>();
            HttpRequest<?> request = HttpRequest.GET("/");
            RequestDeadline deadline = RequestDeadline.start(request, Duration.ofMillis(20), PropagatedContext.empty(),
                () -> expiredOn.set(Thread.currentThread().getName()), scheduler);
            ExecutionFlow<HttpResponse<?>> bound = deadline.bind(DelayedExecutionFlow.create(), r -> { });
            assertInstanceOf(RequestTimeoutException.class, failureOf(bound));
            assertTrue(expiredOn.get() != null, "the expiry ran");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void theEndHookRunsOnceWhenTheRouteAnswers() {
        HttpRequest<?> request = HttpRequest.GET("/");
        RequestDeadline deadline = start(request, Duration.ofSeconds(5));
        AtomicBoolean ran = new AtomicBoolean();
        deadline.onEnd(() -> assertFalse(ran.getAndSet(true), "the hook ran twice"));
        assertTrue(deadline.remaining().isPresent(), "the deadline runs");
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        deadline.bind(route, response -> { });
        route.complete(HttpResponse.ok());
        deadline.abandon();
        assertTrue(ran.get());
    }
    @Test
    void aReactiveRouteInTimeAnswers() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        HttpResponse<?> response = HttpResponse.ok();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(ReactiveExecutionFlow.fromPublisher(Mono.just(response)), r -> { });
        assertSame(response, bound.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertTrue(deadline.remaining().isEmpty(), "the deadline ended");
        assertEquals(Duration.ofSeconds(5), deadline.getTimeout());
    }

    @Test
    void aReactiveRouteThatFailsInTimeFails() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        IllegalStateException failure = new IllegalStateException("boom");
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(ReactiveExecutionFlow.fromPublisher(Mono.error(failure)), r -> { });
        assertSame(failure, failureOf(bound));
    }

    @Test
    void anEmptyReactiveRouteCompletesEmpty() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(ReactiveExecutionFlow.fromPublisher(Mono.empty()), r -> { });
        assertEquals(null, bound.toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void aSlowReactiveRouteTimesOutAndALateResponseIsDiscarded() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofMillis(50));
        Sinks.One<HttpResponse<?>> sink = Sinks.one();
        AtomicReference<HttpResponse<?>> discarded = new AtomicReference<>();
        CountDownLatch cancelled = new CountDownLatch(1);
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(
            ReactiveExecutionFlow.fromPublisher(sink.asMono().doOnCancel(cancelled::countDown)), discarded::set);
        assertInstanceOf(RequestTimeoutException.class, failureOf(bound));
        assertTrue(cancelled.await(5, TimeUnit.SECONDS), "the route was cancelled");
        assertTrue(deadline.isExpired());
    }

    @Test
    void aCompletedRouteBoundAfterTheExpiryIsDiscarded() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofMillis(1));
        awaitExpiry(deadline);
        HttpResponse<?> late = HttpResponse.ok();
        AtomicReference<HttpResponse<?>> discarded = new AtomicReference<>();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(ExecutionFlow.just(late), discarded::set);
        assertInstanceOf(RequestTimeoutException.class, failureOf(bound));
        assertSame(late, discarded.get());
    }

    @Test
    void aCompletedRouteInTimeIsReturnedAsIs() {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        ExecutionFlow<HttpResponse<?>> flow = ExecutionFlow.just(HttpResponse.ok());
        assertSame(flow, deadline.bind(flow, r -> { }));
    }

    @Test
    void aFailedRouteInTimeFails() throws Exception {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        DelayedExecutionFlow<HttpResponse<?>> route = DelayedExecutionFlow.create();
        ExecutionFlow<HttpResponse<?>> bound = deadline.bind(route, r -> { });
        IllegalStateException failure = new IllegalStateException("boom");
        route.completeExceptionally(failure);
        assertSame(failure, failureOf(bound));
    }

    @Test
    void anEndHookAddedAfterTheEndRunsAtOnce() {
        RequestDeadline deadline = start(HttpRequest.GET("/"), Duration.ofSeconds(5));
        deadline.abandon();
        deadline.abandon();
        AtomicBoolean ran = new AtomicBoolean();
        deadline.onEnd(() -> ran.set(true));
        assertTrue(ran.get());
        assertFalse(deadline.isExpired());
    }

    @Test
    void anOffloadedRouteRunsOnItsExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            HttpResponse<?> response = HttpResponse.ok();
            ExecutionFlow<HttpResponse<?>> flow = RouteExecution.offload(executor, HttpRequest.GET("/"), () -> ExecutionFlow.just(response));
            assertSame(response, flow.toCompletableFuture().get(5, TimeUnit.SECONDS));
            IllegalStateException failure = new IllegalStateException("boom");
            assertSame(failure, failureOf(RouteExecution.offload(executor, HttpRequest.GET("/"), () -> {
                throw failure;
            })));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anOffloadedRouteReceivesTheCancellation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DelayedExecutionFlow<HttpResponse<?>> inner = DelayedExecutionFlow.create();
            CountDownLatch started = new CountDownLatch(1);
            ExecutionFlow<HttpResponse<?>> flow = RouteExecution.offload(executor, HttpRequest.GET("/"), () -> {
                started.countDown();
                return inner;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            // the route registers for the cancellation once it ran
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            flow.cancel();
            assertTrue(inner.isCancelled(), "the cancellation reached the route");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aQueuedRouteWhoseDeadlineExpiredIsNotInvoked() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch release = new CountDownLatch(1);
            executor.execute(() -> {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            HttpRequest<?> request = HttpRequest.GET("/");
            RequestDeadline deadline = start(request, Duration.ofMillis(1));
            AtomicBoolean invoked = new AtomicBoolean();
            ExecutionFlow<HttpResponse<?>> flow = RouteExecution.offload(executor, request, () -> {
                invoked.set(true);
                return ExecutionFlow.just(HttpResponse.ok());
            });
            awaitExpiry(deadline);
            flow.cancel();
            release.countDown();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertFalse(invoked.get(), "the queued route was invoked after its deadline expired");
        } finally {
            executor.shutdownNow();
        }
    }
}
