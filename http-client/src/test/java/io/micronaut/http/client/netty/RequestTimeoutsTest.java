package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.RequestTimeouts;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The read timeout of the {@link RequestTimeouts} of a request applies to its exchange only:
 * the pooled connection, or the other streams of an HTTP/2 connection, keep the read timeout of
 * the client.
 */
class RequestTimeoutsTest {
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aPooledConnectionGetsTheReadTimeoutOfTheClientBack(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            // longer than the read timeout of the client
            String port = client.toBlocking().retrieve(request(1500, Duration.ofSeconds(5)));

            // the same connection, without the timeouts of the request
            Assertions.assertEquals(port, client.toBlocking().retrieve(request(0, null)));
            assertTimesOutWithin(Mono.from(client.retrieve(request(3000, null))).toFuture(), Duration.ofMillis(1700));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentExchangesWithDifferentReadTimeouts(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            // opens the connection
            String port = client.toBlocking().retrieve(request(0, null));

            CompletableFuture<String> longer = Mono.from(client.retrieve(request(1500, Duration.ofSeconds(5)))).toFuture();
            CompletableFuture<String> shorter = Mono.from(client.retrieve(request(3000, Duration.ofMillis(200)))).toFuture();

            assertTimesOutWithin(shorter, Duration.ofMillis(900));
            String longerPort = longer.get(10, TimeUnit.SECONDS);
            if (http2) {
                // both are streams of the same connection
                Assertions.assertEquals(port, longerPort);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aCancelledExchangeLeavesTheReadTimeoutOfTheClient(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            client.toBlocking().retrieve(request(0, null));

            Disposable cancelled = Mono.from(client.retrieve(request(3000, Duration.ofSeconds(10)))).subscribe();
            TimeUnit.MILLISECONDS.sleep(300);
            cancelled.dispose();

            Assertions.assertNotNull(client.toBlocking().retrieve(request(0, null)));
            assertTimesOutWithin(Mono.from(client.retrieve(request(3000, null))).toFuture(), Duration.ofMillis(1700));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFilterDoesNotChangeTheOverallLimit(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            // the URL of the client is fixed, so the exchange runs up to the filters at once
            MutableHttpRequest<?> request = HttpRequest.GET("/request-timeouts/slow?delay=600")
                .header(TimeoutsFilter.REQUEST_TIMEOUT, "PT0.3S");
            Assertions.assertNotNull(client.toBlocking().retrieve(request));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFilterCanChangeTheReadTimeout(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/request-timeouts/slow?delay=3000")
                .header(TimeoutsFilter.READ_TIMEOUT, "PT0.2S");
            assertTimesOutWithin(Mono.from(client.retrieve(request)).toFuture(), Duration.ofMillis(900));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void theRequestTimeoutOfTheConfigurationLimitsALongerReadTimeout(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2, Map.of("micronaut.http.client.request-timeout", "700ms"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            // the overall limit is the request timeout of the configuration, not the read timeout
            // of the request plus one second
            assertTimesOutWithin(Mono.from(client.retrieve(request(1500, Duration.ofSeconds(5)))).toFuture(), Duration.ofMillis(1200));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void theRequestTimeoutOfTheRequestReplacesTheOneOfTheConfiguration(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2, Map.of("micronaut.http.client.request-timeout", "700ms"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/request-timeouts/slow?delay=1500");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder()
                .readTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(5))
                .build());
            Assertions.assertNotNull(client.toBlocking().retrieve(request));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void theReadIdleTimeoutOfARawExchangeTakesPrecedence(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            MutableHttpRequest<?> request = HttpRequest.GET(server.getURI().resolve("/request-timeouts/slow?delay=1500"));
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(200)).build());
            RawRequestOptions options = RawRequestOptions.builder().readIdleTimeout(Duration.ofSeconds(5)).build();
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) client.toAsyncRaw().exchange(request, null, options)
                .toCompletableFuture().get(10, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertFalse(response.byteBody().buffer().get(10, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8).isEmpty());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aRawExchangeTakesTheReadTimeoutOfTheRequest(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            MutableHttpRequest<?> request = HttpRequest.GET(server.getURI().resolve("/request-timeouts/slow?delay=3000"));
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(200)).build());
            CompletableFuture<?> pending = client.toAsyncRaw().exchange(request, null, RawRequestOptions.getDefault()).toCompletableFuture();
            assertTimesOutWithin(pending, Duration.ofMillis(900));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aStreamingExchangeTakesTheReadTimeoutOfTheRequest(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, server.getURL())) {
            // a pause longer than the read timeout of the client
            Assertions.assertEquals(List.of("a", "b"), stream(client, 1500, RequestTimeouts.builder().readTimeout(Duration.ofSeconds(5)).build())
                .get(10, TimeUnit.SECONDS));
            // a pause longer than the read timeout of the request
            assertTimesOutWithin(stream(client, 3000, RequestTimeouts.builder().readTimeout(Duration.ofMillis(200)).build()), Duration.ofMillis(900));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aStreamingExchangeHasNoOverallLimit(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, server.getURL())) {
            Assertions.assertEquals(List.of("a", "b"), stream(client, 600, RequestTimeouts.builder().requestTimeout(Duration.ofMillis(300)).build())
                .get(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aProxiedRequestTakesTheReadTimeoutOfTheRequest(boolean http2) throws Exception {
        try (ApplicationContext ctx = start(http2);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            // closed with the context
            ProxyHttpClient client = ctx.createBean(ProxyHttpClient.class);
            // longer than the read timeout of the client
            MutableHttpRequest<?> longer = HttpRequest.GET(server.getURI().resolve("/request-timeouts/slow?delay=1500"));
            ClientAttributes.setTimeouts(longer, RequestTimeouts.builder().readTimeout(Duration.ofSeconds(5)).build());
            Assertions.assertEquals(200, Mono.from(client.proxy(longer)).toFuture().get(10, TimeUnit.SECONDS).code());

            MutableHttpRequest<?> shorter = HttpRequest.GET(server.getURI().resolve("/request-timeouts/slow?delay=3000"));
            ClientAttributes.setTimeouts(shorter, RequestTimeouts.builder().readTimeout(Duration.ofMillis(200)).build());
            assertTimesOutWithin(Mono.from(client.proxy(shorter)).toFuture(), Duration.ofMillis(900));
        }
    }

    private static CompletableFuture<List<String>> stream(StreamingHttpClient client, long delay, RequestTimeouts timeouts) {
        MutableHttpRequest<?> request = HttpRequest.GET("/request-timeouts/stream?delay=" + delay);
        ClientAttributes.setTimeouts(request, timeouts);
        return Flux.from(client.dataStream(request))
            .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
            .collectList()
            .toFuture();
    }

    private static void assertTimesOutWithin(CompletableFuture<?> pending, Duration limit) {
        long start = System.nanoTime();
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
        Assertions.assertInstanceOf(ReadTimeoutException.class, failure.getCause(), () -> String.valueOf(failure.getCause()));
        Duration timedOutAfter = Duration.ofNanos(System.nanoTime() - start);
        Assertions.assertTrue(timedOutAfter.compareTo(limit) < 0, "Timed out after " + timedOutAfter);
    }

    private static ApplicationContext start(boolean http2) {
        return start(http2, Map.of());
    }

    private static ApplicationContext start(boolean http2, Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("spec.name", "RequestTimeoutsTest");
        all.put("micronaut.http.client.plaintext-mode", http2 ? "h2c_prior_knowledge" : "http_1");
        all.put("micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms");
        all.put("micronaut.server.http-version", http2 ? "2.0" : "1.1");
        return ApplicationContext.run(all);
    }

    private static HttpRequest<?> request(long delay, Duration readTimeout) {
        MutableHttpRequest<?> request = HttpRequest.GET("/request-timeouts/slow?delay=" + delay);
        if (readTimeout != null) {
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(readTimeout).build());
        }
        return request;
    }

    @ClientFilter("/request-timeouts/**")
    @Requires(property = "spec.name", value = "RequestTimeoutsTest")
    static class TimeoutsFilter {
        static final String REQUEST_TIMEOUT = "X-Request-Timeout";
        static final String READ_TIMEOUT = "X-Read-Timeout";

        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            String requestTimeout = request.getHeaders().get(REQUEST_TIMEOUT);
            if (requestTimeout != null) {
                ClientAttributes.setTimeouts(request, RequestTimeouts.builder().requestTimeout(Duration.parse(requestTimeout)).build());
            }
            String readTimeout = request.getHeaders().get(READ_TIMEOUT);
            if (readTimeout != null) {
                ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.parse(readTimeout)).build());
            }
        }
    }

    @Controller("/request-timeouts")
    @Requires(property = "spec.name", value = "RequestTimeoutsTest")
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay, HttpRequest<?> request) {
            // the port of the client identifies its connection
            String port = String.valueOf(request.getRemoteAddress().getPort());
            return Mono.delay(Duration.ofMillis(delay)).thenReturn(port);
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Flux<String> stream(@QueryValue long delay) {
            return Flux.concat(Mono.just("a"), Mono.delay(Duration.ofMillis(delay)).thenReturn("b"));
        }
    }
}
