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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException;
import io.micronaut.http.client.limit.ClientConcurrencyLimiter;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * The cap of exchanges in flight of a service client: an exchange over it fails at once, and
 * every permit is given back, whether the exchange completes, is cancelled, follows a redirect, or
 * streams its body.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class ConcurrencyLimitTest {
    static final String SPEC_NAME = "ConcurrencyLimitTest";
    private static final String SERVICE = "capped";

    @Test
    void anExchangeOverTheCapFails() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();

            CompletableFuture<String> first = Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture();
            awaitInFlight(limiter, 1);
            Throwable failure = failure(Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture());
            ConcurrencyLimitExceededException limit = Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class, failure);
            Assertions.assertEquals(SERVICE, limit.getLimiterName());
            Assertions.assertEquals(1, limiter.getRejectedCount());

            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            awaitInFlight(limiter, 0);
            gate.open();
            Assertions.assertEquals("open", client.toBlocking().retrieve("/concurrency-limit/slow"));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void aCancelledExchangeGivesItsPermitBack() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            gate.close();

            Disposable inFlight = Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).subscribe(v -> { }, e -> { });
            awaitInFlight(limiter, 1);
            inFlight.dispose();
            awaitInFlight(limiter, 0);

            gate.open();
            Assertions.assertEquals("open", client.toBlocking().retrieve("/concurrency-limit/slow"));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void anAbsoluteUriCountsAgainstTheCap() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();
            String absolute = server.getURL().orElseThrow() + "/concurrency-limit/slow";

            CompletableFuture<String> first = Mono.from(client.retrieve(HttpRequest.GET(absolute), String.class)).toFuture();
            awaitInFlight(limiter, 1);
            Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                failure(Mono.from(client.retrieve(HttpRequest.GET(absolute), String.class)).toFuture()));
            Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                failure(Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture()));

            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void aStreamedResponseHoldsItsPermitUntilTheBodyEnds() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient httpClient = client(clients);
            Assumptions.assumeTrue(httpClient instanceof StreamingHttpClient, "The client does not stream");
            StreamingHttpClient client = (StreamingHttpClient) httpClient;
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();

            CompletableFuture<String> firstChunk = new CompletableFuture<>();
            CompletableFuture<List<String>> chunks = Flux.from(client.dataStream(HttpRequest.GET("/concurrency-limit/stream")))
                .map(ConcurrencyLimitTest::text)
                .doOnNext(firstChunk::complete)
                .collectList()
                .toFuture();
            Assertions.assertEquals("first", firstChunk.get(30, TimeUnit.SECONDS));
            // the response arrived, its body did not end yet
            Assertions.assertEquals(1, limiter.getInFlight());
            Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                failure(Mono.from(client(clients).retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture()));

            pending.complete("last");
            Assertions.assertEquals("firstlast", String.join("", chunks.get(30, TimeUnit.SECONDS)));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void aPermitIsHeldAcrossARedirect() throws Exception {
        assertPermitHeldAcrossRedirect("/concurrency-limit/redirect", Map.of());
    }

    @Test
    void aPermitIsHeldAcrossARedirectToAnAbsoluteLocation() throws Exception {
        assertPermitHeldAcrossRedirect("/concurrency-limit/redirect-absolute", Map.of());
    }

    @Test
    void aPermitIsHeldAcrossARedirectFollowedByMicronaut() throws Exception {
        // the JDK client then leaves the redirects to the shared redirect handling
        assertPermitHeldAcrossRedirect("/concurrency-limit/redirect", Map.of(
            "micronaut.http.services." + SERVICE + ".jdk.use-micronaut-redirects", true));
    }

    @Test
    void anInvalidRedirectGivesItsPermitBack() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            for (int i = 0; i < 3; i++) {
                Assertions.assertNotNull(failure(Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/redirect-invalid"), String.class)).toFuture()));
                awaitInFlight(limiter, 0);
            }
            Assertions.assertEquals("open", client.toBlocking().retrieve("/concurrency-limit/slow"));
        }
    }

    @Test
    void aRequestSentWhenTheFirstCompletesGetsThePermit() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            // the second request starts in the continuation of the first, before anything else
            String both = Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class))
                .flatMap(first -> Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).map(second -> first + second))
                .toFuture().get(30, TimeUnit.SECONDS);
            Assertions.assertEquals("openopen", both);
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void aFilterThatChangesTheOriginKeepsThePermit() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of())) {
            HttpClient httpClient = client(clients);
            Assumptions.assumeTrue(httpClient instanceof StreamingHttpClient, "The client does not stream");
            StreamingHttpClient client = (StreamingHttpClient) httpClient;
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();

            CompletableFuture<String> firstChunk = new CompletableFuture<>();
            CompletableFuture<List<String>> chunks = Flux.from(client.dataStream(HttpRequest.GET("/concurrency-limit/rewrite")))
                .map(ConcurrencyLimitTest::text)
                .doOnNext(firstChunk::complete)
                .collectList()
                .toFuture();
            Assertions.assertEquals("first", firstChunk.get(30, TimeUnit.SECONDS));
            // the filter sent the request to another origin; its body did not end yet
            Assertions.assertEquals(1, limiter.getInFlight());

            pending.complete("last");
            Assertions.assertEquals("firstlast", String.join("", chunks.get(30, TimeUnit.SECONDS)));
            awaitInFlight(limiter, 0);
        }
    }

    private static void assertPermitHeldAcrossRedirect(String path, Map<String, Object> extra) throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, extra)) {
            HttpClient client = client(clients);
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();

            CompletableFuture<String> first = Mono.from(client.retrieve(HttpRequest.GET(path), String.class)).toFuture();
            awaitInFlight(limiter, 1);
            // the redirect was answered, its target was not: the exchange still holds the permit
            Thread.sleep(200);
            if (first.isDone()) {
                Assertions.fail("The redirect was not followed with the permit: " + first.handle((r, e) -> e == null ? r : String.valueOf(e)).get());
            }
            Assertions.assertEquals(1, limiter.getInFlight());
            Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                failure(Mono.from(client.retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture()));

            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void aStreamedRedirectFollowedByMicronautHoldsThePermit() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(server, Map.of(
                 "micronaut.http.services." + SERVICE + ".jdk.use-micronaut-redirects", true))) {
            HttpClient httpClient = client(clients);
            Assumptions.assumeTrue(httpClient instanceof StreamingHttpClient, "The client does not stream");
            StreamingHttpClient client = (StreamingHttpClient) httpClient;
            ClientConcurrencyLimiter limiter = limiter(clients);
            Gate gate = server.getApplicationContext().getBean(Gate.class);
            CompletableFuture<String> pending = gate.close();

            CompletableFuture<String> firstChunk = new CompletableFuture<>();
            CompletableFuture<List<String>> chunks = Flux.from(client.dataStream(HttpRequest.GET("/concurrency-limit/redirect-stream")))
                .map(ConcurrencyLimitTest::text)
                .doOnNext(firstChunk::complete)
                .collectList()
                .toFuture();
            Assertions.assertEquals("first", firstChunk.get(30, TimeUnit.SECONDS));
            // the redirect was answered and followed, the body of its target did not end yet
            Assertions.assertEquals(1, limiter.getInFlight());
            Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                failure(Mono.from(client(clients).retrieve(HttpRequest.GET("/concurrency-limit/slow"), String.class)).toFuture()));

            pending.complete("last");
            Assertions.assertEquals("firstlast", String.join("", chunks.get(30, TimeUnit.SECONDS)));
            awaitInFlight(limiter, 0);
        }
    }

    @Test
    void theCapIsOffByDefault() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = ApplicationContext.run(Map.of(
                 "spec.name", SPEC_NAME + "-client",
                 "micronaut.http.services." + SERVICE + ".url", server.getURL().orElseThrow().toString()))) {
            ServiceHttpClientConfiguration configuration = clients.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(SERVICE));
            Assertions.assertNull(configuration.getConcurrencyLimiter());
            Assertions.assertEquals("open", client(clients).toBlocking().retrieve("/concurrency-limit/slow"));
        }
    }

    private static String text(ByteBuffer<?> buffer) {
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Throwable failure(CompletableFuture<?> future) {
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        return e.getCause();
    }

    private static void awaitInFlight(ClientConcurrencyLimiter limiter, int expected) {
        // the permit is given back on the event loop, just after the caller got the response
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (limiter.getInFlight() != expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        Assertions.assertEquals(expected, limiter.getInFlight(), "Exchanges in flight");
    }

    private static ApplicationContext clients(ServerUnderTest server, Map<String, Object> extra) {
        Map<String, Object> properties = new HashMap<>(Map.of(
            "spec.name", SPEC_NAME + "-client",
            "micronaut.http.services." + SERVICE + ".url", server.getURL().orElseThrow().toString(),
            "micronaut.http.services." + SERVICE + ".read-timeout", "60s",
            "micronaut.http.services." + SERVICE + ".max-concurrent-requests", 1
        ));
        properties.putAll(extra);
        return ApplicationContext.run(properties);
    }

    private static HttpClient client(ApplicationContext context) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(SERVICE));
        return context.getBean(HttpClientRegistry.class).getClient(HttpVersionSelection.forClientConfiguration(configuration), SERVICE, null);
    }

    private static ClientConcurrencyLimiter limiter(ApplicationContext context) {
        ClientConcurrencyLimiter limiter = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(SERVICE)).getConcurrencyLimiter();
        Assertions.assertNotNull(limiter);
        return limiter;
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Gate {
        private final AtomicReference<CompletableFuture<String>> pending = new AtomicReference<>(CompletableFuture.completedFuture("open"));

        CompletableFuture<String> close() {
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.set(future);
            return future;
        }

        void open() {
            pending.set(CompletableFuture.completedFuture("open"));
        }

        CompletableFuture<String> get() {
            return pending.get();
        }
    }

    @Controller("/concurrency-limit")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SlowController {
        private final Gate gate;

        SlowController(Gate gate) {
            this.gate = gate;
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> slow() {
            return gate.get();
        }

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.redirect(URI.create("/concurrency-limit/slow"));
        }

        @Get("/redirect-absolute")
        HttpResponse<?> redirectAbsolute(HttpRequest<?> request) {
            return HttpResponse.redirect(URI.create("http://" + request.getHeaders().get("Host") + "/concurrency-limit/slow"));
        }

        @Get("/redirect-invalid")
        HttpResponse<?> redirectInvalid() {
            // not a URI: the client cannot follow it
            return HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://bad host/^{}");
        }

        @Get("/redirect-stream")
        HttpResponse<?> redirectStream() {
            return HttpResponse.redirect(URI.create("/concurrency-limit/stream"));
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Publisher<String> stream() {
            return Flux.concat(Mono.just("first"), Mono.fromFuture(gate.get()));
        }
    }

    /**
     * Sends the request of {@code /concurrency-limit/rewrite} to the stream endpoint, at another
     * origin of the same server: {@code 127.0.0.1} for {@code localhost}, and the other way round.
     */
    @ClientFilter("/concurrency-limit/rewrite")
    @Requires(property = "spec.name", value = SPEC_NAME + "-client")
    static class OriginRewritingFilter {
        @RequestFilter
        void rewrite(MutableHttpRequest<?> request) {
            URI uri = request.getUri();
            String host = "localhost".equalsIgnoreCase(uri.getHost()) ? "127.0.0.1" : "localhost";
            request.uri(URI.create(uri.getScheme() + "://" + host + ":" + uri.getPort() + "/concurrency-limit/stream"));
        }
    }
}
