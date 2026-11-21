package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A filter method that returns a {@code CompletionStage<Void>} or a {@code Publisher<Void>} delays
 * the filter chain until it completes, and fails the request if it fails.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilterVoidAsyncTest {
    static final String SPEC_NAME = "FilterVoidAsyncTest";
    static final String MODE = "X-Mode";
    static final String DONE = "filter-done";
    static final String RESPONSE_DONE = "X-Response-Done";
    static final String CLIENT_DONE = "X-Client-Done";
    static final long DELAY_MILLIS = 200;

    // the threads of this executor are created by the event loop and inherit the resource scope
    // of the leak detector, unlike those of the common pool
    private static ScheduledExecutorService executor;

    private EmbeddedServer server;
    private HttpClient client;
    private Ctrl ctrl;

    @BeforeAll
    void startServer() {
        executor = Executors.newSingleThreadScheduledExecutor();
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURI());
        ctrl = server.getApplicationContext().getBean(Ctrl.class);
    }

    @AfterAll
    void stopServer() {
        client.close();
        server.close();
        executor.shutdownNow();
    }

    @BeforeEach
    void reset() {
        ctrl.calls.set(0);
    }

    @Test
    void requestFilterWaitsForTheCompletionStage() {
        assertEquals("stage", exchange("stage").body());
        assertEquals(1, ctrl.calls.get());
    }

    @Test
    void requestFilterFailsWithTheCompletionStage() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> exchange("stage-fail"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(0, ctrl.calls.get());
    }

    @Test
    void requestFilterWaitsForTheMono() {
        assertEquals("mono", exchange("mono").body());
        assertEquals(1, ctrl.calls.get());
    }

    @Test
    void requestFilterWaitsForThePublisher() {
        assertEquals("publisher", exchange("publisher").body());
        assertEquals(1, ctrl.calls.get());
    }

    @Test
    void requestFilterFailsWithTheMono() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> exchange("mono-fail"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(0, ctrl.calls.get());
    }

    @Test
    void responseFilterWaitsForTheCompletionStage() {
        HttpResponse<String> response = exchange("response");
        assertEquals("true", response.header(RESPONSE_DONE));
    }

    @Test
    void responseFilterFailsWithTheMono() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> exchange("response-fail"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void clientRequestFilterWaitsForTheCompletionStage() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/filter-void/client").header(MODE, "client"), String.class);
        assertEquals("true", response.body());
    }

    private HttpResponse<String> exchange(String mode) {
        return client.toBlocking().exchange(HttpRequest.GET("/filter-void").header(MODE, mode), String.class);
    }

    private static CompletableFuture<Void> later(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        // the outcome is reported through the future, not the scheduled task
        var _ = executor.schedule(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        }, DELAY_MILLIS, TimeUnit.MILLISECONDS);
        return future;
    }

    private static Mono<Void> laterMono(Runnable action) {
        // nothing happens unless the filter runner subscribes
        return Mono.defer(() -> Mono.fromCompletionStage(later(action)));
    }

    private static HttpStatusException failure() {
        return new HttpStatusException(HttpStatus.CONFLICT, "filter failed");
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/filter-void/**")
    static final class VoidFilters {
        @RequestFilter
        CompletionStage<Void> stage(HttpRequest<?> request) {
            String mode = request.getHeaders().get(MODE);
            if ("stage".equals(mode)) {
                return later(() -> request.setAttribute(DONE, mode));
            } else if ("stage-fail".equals(mode)) {
                return later(() -> {
                    throw failure();
                });
            }
            return CompletableFuture.completedFuture(null);
        }

        @RequestFilter
        Mono<Void> mono(HttpRequest<?> request) {
            String mode = request.getHeaders().get(MODE);
            if ("mono".equals(mode)) {
                return laterMono(() -> request.setAttribute(DONE, mode));
            } else if ("mono-fail".equals(mode)) {
                return laterMono(() -> {
                    throw failure();
                });
            }
            return Mono.empty();
        }

        @RequestFilter
        Publisher<Void> publisher(HttpRequest<?> request) {
            String mode = request.getHeaders().get(MODE);
            if ("publisher".equals(mode)) {
                return laterMono(() -> request.setAttribute(DONE, mode));
            }
            return Mono.empty();
        }

        @ResponseFilter
        CompletionStage<Void> response(HttpRequest<?> request, MutableHttpResponse<?> response) {
            if ("response".equals(request.getHeaders().get(MODE))) {
                return later(() -> response.header(RESPONSE_DONE, "true"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @ResponseFilter
        Mono<Void> responseFail(HttpRequest<?> request) {
            if ("response-fail".equals(request.getHeaders().get(MODE))) {
                return laterMono(() -> {
                    throw failure();
                });
            }
            return Mono.empty();
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ClientFilter("/filter-void/client")
    static final class VoidClientFilter {
        @RequestFilter
        CompletionStage<Void> stage(MutableHttpRequest<?> request) {
            return later(() -> request.header(CLIENT_DONE, "true"));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/filter-void")
    static final class Ctrl {
        final AtomicInteger calls = new AtomicInteger();

        @Get
        String index(HttpRequest<?> request) {
            calls.incrementAndGet();
            // only set once the filter's stage or publisher completed
            return request.getAttribute(DONE, String.class).orElse("not waited");
        }

        @Get("/client")
        String client(@Header(CLIENT_DONE) @Nullable String done) {
            return String.valueOf(done);
        }
    }
}
