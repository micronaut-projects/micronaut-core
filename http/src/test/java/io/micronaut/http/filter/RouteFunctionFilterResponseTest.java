package io.micronaut.http.filter;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A response filter declared as a function that continues with the response it was given, like a
 * void {@code @ResponseFilter} method, does not replace it: the response is not processed again as
 * a new one, e.g. looking up a status route for an error response.
 */
class RouteFunctionFilterResponseTest {

    @Test
    void aFilterThatChangesTheResponseInPlaceDoesNotReplaceIt() {
        var processed = new AtomicInteger();
        HttpResponse<?> route = HttpResponse.notFound();
        GenericHttpFilter filter = RouteFunctionFilter.response((request, response, propagatedContext) -> {
            response.header("X-Filtered", "true");
            return null;
        }, null);

        HttpResponse<?> response = run(List.of(filter), route, processed);

        assertSame(route, response);
        assertEquals("true", response.getHeaders().get("X-Filtered"));
        assertEquals(0, processed.get());
    }

    @Test
    void aFilterThatReturnsTheResponseItWasGivenDoesNotReplaceIt() {
        var processed = new AtomicInteger();
        HttpResponse<?> route = HttpResponse.notFound();
        GenericHttpFilter filter = RouteFunctionFilter.response((request, response, propagatedContext) -> response, null);

        HttpResponse<?> response = run(List.of(filter), route, processed);

        assertSame(route, response);
        assertEquals(0, processed.get());
    }

    @Test
    void aFilterThatReturnsAnotherResponseReplacesIt() {
        var processed = new AtomicInteger();
        GenericHttpFilter filter = RouteFunctionFilter.response((request, response, propagatedContext) -> HttpResponse.status(HttpStatus.CONFLICT), null);

        HttpResponse<?> response = run(List.of(filter), HttpResponse.notFound(), processed);

        assertEquals(HttpStatus.CONFLICT, response.getStatus());
        assertEquals(1, processed.get());
    }

    @Test
    void aFilterThatChangesThePropagatedContextContinuesWithTheChangedContext() {
        var processed = new AtomicInteger();
        var element = new PropagatedContextElement() {
        };
        GenericHttpFilter filter = RouteFunctionFilter.response((request, response, propagatedContext) -> {
            propagatedContext.add(element);
            return null;
        }, null);
        var seen = new AtomicInteger();
        GenericHttpFilter after = RouteFunctionFilter.response((request, response, propagatedContext) -> {
            if (propagatedContext.getContext().getAllElements().contains(element)) {
                seen.incrementAndGet();
            }
            return null;
        }, null);

        // response filters run in reverse order
        run(List.of(after, filter), HttpResponse.ok(), processed);

        assertEquals(1, seen.get());
    }

    private static HttpResponse<?> run(List<GenericHttpFilter> filters, HttpResponse<?> route, AtomicInteger processed) {
        FilterRunner runner = new FilterRunner(filters, (request, propagatedContext) -> ExecutionFlow.just(route)) {
            @Override
            protected ExecutionFlow<HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response, PropagatedContext propagatedContext) {
                processed.incrementAndGet();
                return ExecutionFlow.just(response);
            }
        };
        return runner.run(HttpRequest.GET("/")).tryCompleteValue();
    }
}
