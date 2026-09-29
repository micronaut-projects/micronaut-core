package io.micronaut.http.server.netty.filters;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An asynchronous filter that changes the {@link MutablePropagatedContext}, when it is called or
 * when its stage completes, and returns a stage that completes with {@code null} after the filter
 * method returned.
 */
class AsyncFilterMutablePropagatedContextTest {
    private static final String SPEC_NAME = "AsyncFilterMutablePropagatedContextTest";

    private final EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
    private final HttpClient httpClient = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

    @AfterEach
    void cleanup() {
        httpClient.close();
        embeddedServer.close();
    }

    @Test
    void requestFilterReturningANullResponseLater() {
        assertOk("/async-context/nullable-response", "request");
    }

    @Test
    void requestFilterChangingTheContextWhenItsStageCompletes() {
        assertOk("/async-context/on-completion", "on-completion");
    }

    @Test
    void responseFilterReturningANullResponseLater() {
        assertOk("/async-context/response", "none");
    }

    @Test
    void requestFilterWithoutAChangeReturningANullResponseLater() {
        assertOk("/async-context/unchanged", "none");
    }

    private void assertOk(String uri, String expected) {
        HttpResponse<String> response = httpClient.toBlocking().exchange(HttpRequest.GET(uri), String.class);
        assertEquals(200, response.code());
        assertEquals(expected, response.body());
    }

    /**
     * @param io The IO executor
     * @return A stage that completes with {@code null} on the IO executor, after the filter returned it
     */
    static <T> CompletableFuture<@Nullable T> completeLater(ExecutorService io) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }, io);
    }

    abstract static class IoFilter {
        final ExecutorService io;

        IoFilter(ExecutorService io) {
            this.io = io;
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/async-context")
    static class TheController {
        @Get(value = "/{name}", produces = "text/plain")
        String get(String name) {
            return PropagatedContext.get().find(Marker.class).map(Marker::name).orElse("none");
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/async-context/nullable-response")
    static class NullableResponseFilter extends IoFilter {
        NullableResponseFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            super(io);
        }

        @RequestFilter
        CompletableFuture<@Nullable HttpResponse<?>> filter(MutablePropagatedContext context) {
            context.add(new Marker("request"));
            return completeLater(io);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/async-context/on-completion")
    static class OnCompletionFilter extends IoFilter {
        OnCompletionFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            super(io);
        }

        @RequestFilter
        CompletableFuture<@Nullable HttpResponse<?>> filter(MutablePropagatedContext context) {
            return AsyncFilterMutablePropagatedContextTest.<HttpResponse<?>>completeLater(io).thenApply(response -> {
                context.add(new Marker("on-completion"));
                return response;
            });
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/async-context/unchanged")
    static class UnchangedFilter extends IoFilter {
        UnchangedFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            super(io);
        }

        @RequestFilter
        CompletableFuture<@Nullable HttpResponse<?>> filter() {
            return completeLater(io);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/async-context/response")
    static class ChangingResponseFilter extends IoFilter {
        ChangingResponseFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            super(io);
        }

        @ResponseFilter
        CompletableFuture<@Nullable MutableHttpResponse<?>> filter(MutablePropagatedContext context) {
            context.add(new Marker("response"));
            return completeLater(io);
        }
    }

    record Marker(String name) implements PropagatedContextElement {
    }
}
