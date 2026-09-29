package io.micronaut.http.filter;

import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route match that completes later, e.g. of an asynchronous route locator: its failure is
 * processed once, and a failure of what runs after it is not processed as a failure of the route
 * match again.
 */
class FilterRunnerAsyncRouteMatchTest {

    @Test
    void aFailureAfterTheRouteMatchIsNotProcessedAgain() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        var routeFailure = new RuntimeException("route failed");
        var handlingFailure = new RuntimeException("handling failed");
        var located = new CompletableFuture<Object>();
        FilterRunner runner = new Runner(List.of(), List.of(proceed()), located, failures, handlingFailure) {
            @Override
            protected ExecutionFlow<HttpResponse<?>> provideResponse(HttpRequest<?> request, PropagatedContext propagatedContext) {
                return ExecutionFlow.error(routeFailure);
            }
        };

        CompletableFuture<HttpResponse<?>> result = runner.run(HttpRequest.GET("/")).toCompletableFuture();
        located.complete(Boolean.TRUE);

        ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertSame(handlingFailure, error.getCause());
        // the failure of the route, processed by the filter chain, and not again as the failure
        // of the route match
        assertEquals(List.of(routeFailure), failures);
    }

    @Test
    void aFailingRouteMatchIsProcessedOnce() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        var matchFailure = new RuntimeException("match failed");
        var located = new CompletableFuture<Object>();
        FilterRunner runner = new Runner(List.of(), List.of(proceed()), located, failures, null);

        CompletableFuture<HttpResponse<?>> result = runner.run(HttpRequest.GET("/")).toCompletableFuture();
        located.completeExceptionally(matchFailure);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.get(10, TimeUnit.SECONDS).getStatus());
        assertEquals(List.of(matchFailure), failures);
    }

    @Test
    void theFailureToFindTheFiltersAfterTheRouteMatchIsNotHiddenByThePreMatchingFilters() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        var findFailure = new RuntimeException("find failed");
        var located = new CompletableFuture<Object>();
        FilterRunner runner = new Runner(List.of(proceed()), List.of(), located, failures, null) {
            @Override
            protected List<GenericHttpFilter> findFiltersAfterRouteMatch(HttpRequest<?> request) {
                throw findFailure;
            }
        };

        CompletableFuture<HttpResponse<?>> result = runner.run(HttpRequest.GET("/")).toCompletableFuture();
        located.complete(Boolean.TRUE);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.get(10, TimeUnit.SECONDS).getStatus());
        assertEquals(List.of(findFailure), failures);
    }

    @Test
    void aFailingRouteMatchIsProcessedOnceAfterThePreMatchingFilters() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        var matchFailure = new RuntimeException("match failed");
        var located = new CompletableFuture<Object>();
        FilterRunner runner = new Runner(List.of(proceed()), List.of(proceed()), located, failures, null);

        CompletableFuture<HttpResponse<?>> result = runner.run(HttpRequest.GET("/")).toCompletableFuture();
        located.completeExceptionally(matchFailure);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.get(10, TimeUnit.SECONDS).getStatus());
        assertEquals(List.of(matchFailure), failures);
    }

    private static GenericHttpFilter proceed() {
        return RouteFunctionFilter.request((request, propagatedContext) -> null, null);
    }

    /**
     * Matches the route when the given future completes, and records the failures it processes.
     */
    private static class Runner extends FilterRunner {
        private final List<GenericHttpFilter> filtersAfterRouteMatch;
        private final CompletableFuture<Object> located;
        private final List<Throwable> failures;
        private final @Nullable RuntimeException handlingFailure;

        Runner(List<GenericHttpFilter> preMatchingFilters,
               List<GenericHttpFilter> filtersAfterRouteMatch,
               CompletableFuture<Object> located,
               List<Throwable> failures,
               @Nullable RuntimeException handlingFailure) {
            super(preMatchingFilters, null, (request, propagatedContext) -> ExecutionFlow.just(HttpResponse.ok()));
            this.filtersAfterRouteMatch = filtersAfterRouteMatch;
            this.located = located;
            this.failures = failures;
            this.handlingFailure = handlingFailure;
        }

        @Override
        protected @Nullable ExecutionFlow<?> doRouteMatchAsync(HttpRequest<?> request) {
            return CompletableFutureExecutionFlow.just(located);
        }

        @Override
        protected List<GenericHttpFilter> findFiltersAfterRouteMatch(HttpRequest<?> request) {
            return filtersAfterRouteMatch;
        }

        @Override
        protected ExecutionFlow<HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure, PropagatedContext propagatedContext) {
            failures.add(failure);
            if (handlingFailure != null) {
                return ExecutionFlow.error(handlingFailure);
            }
            return ExecutionFlow.just(HttpResponse.serverError());
        }
    }
}
