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
package io.micronaut.http.client.limit;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * The paths of an exchange of a capped service client that carry its permit on: an absolute
 * URI, redirects, a Location that cannot be followed, and a filter that changes the origin.
 */
class ConcurrencyCapPathsTest {
    private static final String SPEC = "ConcurrencyCapPathsTest";

    @Test
    void anAbsoluteUriIsCountedAndRejectedOverTheCap() throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<String> pending = f.gate.close();
            String absolute = f.server.getURL() + "/cap-paths/slow";
            CompletableFuture<String> first = Mono.from(f.client.retrieve(HttpRequest.GET(absolute), String.class)).toFuture();
            f.awaitInFlight(1);
            ConcurrencyLimitExceededException e = Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                f.failure(Mono.from(f.client.retrieve(HttpRequest.GET(absolute), String.class)).toFuture()));
            Assertions.assertEquals("capped", e.getLimiterName());
            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            f.awaitInFlight(0);
        }
    }

    @Test
    void redirectsGoOnWithThePermit() throws Exception {
        for (String path : List.of("/cap-paths/redirect", "/cap-paths/redirect-absolute")) {
            try (Fixture f = new Fixture()) {
                CompletableFuture<String> pending = f.gate.close();
                CompletableFuture<String> first = Mono.from(f.client.retrieve(HttpRequest.GET(path), String.class)).toFuture();
                f.awaitInFlight(1);
                Thread.sleep(200);
                Assertions.assertFalse(first.isDone(), path);
                Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class,
                    f.failure(Mono.from(f.client.retrieve(HttpRequest.GET("/cap-paths/slow"), String.class)).toFuture()));
                pending.complete("done");
                Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
                f.awaitInFlight(0);
            }
        }
    }

    @Test
    void aLocationThatCannotBeFollowedGivesThePermitBack() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 3; i++) {
                Assertions.assertNotNull(f.failure(Mono.from(f.client.retrieve(HttpRequest.GET("/cap-paths/redirect-invalid"), String.class)).toFuture()));
                f.awaitInFlight(0);
            }
        }
    }

    @Test
    void tooManyRedirectsGiveThePermitBack() throws Exception {
        try (Fixture f = new Fixture(Map.of("micronaut.http.services.capped.max-redirects", 1))) {
            Assertions.assertNotNull(f.failure(Mono.from(f.client.retrieve(HttpRequest.GET("/cap-paths/redirect-twice"), String.class)).toFuture()));
            f.awaitInFlight(0);
        }
    }

    @Test
    void aFilterThatChangesTheOriginKeepsThePermitUntilTheBodyEnds() throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<String> pending = f.gate.close();
            CompletableFuture<String> firstChunk = new CompletableFuture<>();
            CompletableFuture<List<String>> chunks = Flux.from(((StreamingHttpClient) f.client).dataStream(HttpRequest.GET("/cap-paths/rewrite")))
                .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                .doOnNext(firstChunk::complete)
                .collectList()
                .toFuture();
            Assertions.assertEquals("first", firstChunk.get(30, TimeUnit.SECONDS));
            Assertions.assertEquals(1, f.limiter.getInFlight());
            pending.complete("last");
            Assertions.assertEquals("firstlast", String.join("", chunks.get(30, TimeUnit.SECONDS)));
            f.awaitInFlight(0);
        }
    }

    @Test
    void anExchangeWithTheBypassAttributeIsNotCounted() throws Exception {
        try (Fixture f = new Fixture()) {
            CompletableFuture<String> pending = f.gate.close();
            CompletableFuture<String> first = Mono.from(f.client.retrieve(HttpRequest.GET("/cap-paths/slow"), String.class)).toFuture();
            f.awaitInFlight(1);
            MutableHttpRequest<?> bypass = HttpRequest.GET("/cap-paths/health");
            bypass.setAttribute(AbstractHttpClient.NO_CONCURRENCY_LIMIT, true);
            Assertions.assertEquals("ok", f.client.toBlocking().retrieve(bypass));
            Assertions.assertEquals(1, f.limiter.getInFlight());
            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            f.awaitInFlight(0);
        }
    }

    @Test
    void theHealthCheckOfABusyServiceKeepsItsUrl() throws Exception {
        try (Fixture f = new Fixture(Map.of(
            "micronaut.http.services.capped.health-check", true,
            "micronaut.http.services.capped.health-check-interval", "50ms",
            "micronaut.http.services.capped.health-check-uri", "/cap-paths/health"))) {
            CompletableFuture<String> pending = f.gate.close();
            CompletableFuture<String> first = Mono.from(f.client.retrieve(HttpRequest.GET("/cap-paths/slow"), String.class)).toFuture();
            f.awaitInFlight(1);
            // several rounds of probes while the only permit is taken
            Thread.sleep(500);
            StaticServiceInstanceList instances = f.clients.getBean(StaticServiceInstanceList.class, Qualifiers.byName("capped"));
            Assertions.assertEquals(1, instances.getLoadBalancedURIs().size(), "The probe of a busy service evicted its URL");
            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS));
            // the service is still selectable
            Assertions.assertEquals("ok", f.client.toBlocking().retrieve("/cap-paths/health"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final EmbeddedServer server;
        final ApplicationContext clients;
        final HttpClient client;
        final ClientConcurrencyLimiter limiter;
        final Gate gate;

        Fixture() {
            this(Map.of());
        }

        Fixture(Map<String, Object> extra) {
            server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
            Map<String, Object> properties = new java.util.HashMap<>(Map.of(
                "spec.name", SPEC + "-client",
                "micronaut.http.services.capped.url", server.getURL().toString(),
                "micronaut.http.services.capped.max-concurrent-requests", 1));
            properties.putAll(extra);
            clients = ApplicationContext.run(properties);
            client = clients.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "capped", null);
            limiter = clients.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("capped")).getConcurrencyLimiter();
            gate = server.getApplicationContext().getBean(Gate.class);
        }

        void awaitInFlight(int expected) {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (limiter.getInFlight() != expected && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
            Assertions.assertEquals(expected, limiter.getInFlight());
        }

        Throwable failure(CompletableFuture<?> future) {
            ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
            return e.getCause();
        }

        @Override
        public void close() {
            clients.close();
            server.close();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Gate {
        private final AtomicReference<CompletableFuture<String>> pending = new AtomicReference<>(CompletableFuture.completedFuture("open"));

        CompletableFuture<String> close() {
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.set(future);
            return future;
        }

        CompletableFuture<String> get() {
            return pending.get();
        }
    }

    @Controller("/cap-paths")
    @Requires(property = "spec.name", value = SPEC)
    static class CapPathsController {
        private final Gate gate;

        CapPathsController(Gate gate) {
            this.gate = gate;
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> slow() {
            return gate.get();
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Flux<String> stream() {
            return Flux.concat(Mono.just("first"), Mono.fromFuture(gate.get()));
        }

        @Get(value = "/health", produces = MediaType.TEXT_PLAIN)
        String health() {
            return "ok";
        }

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.redirect(URI.create("/cap-paths/slow"));
        }

        @Get("/redirect-twice")
        HttpResponse<?> redirectTwice() {
            return HttpResponse.redirect(URI.create("/cap-paths/redirect"));
        }

        @Get("/redirect-absolute")
        HttpResponse<?> redirectAbsolute(HttpRequest<?> request) {
            return HttpResponse.redirect(URI.create("http://" + request.getHeaders().get("Host") + "/cap-paths/slow"));
        }

        @Get("/redirect-invalid")
        HttpResponse<?> redirectInvalid() {
            return HttpResponse.status(HttpStatus.FOUND).header("Location", "http://bad host/^{}");
        }
    }

    @ClientFilter("/cap-paths/rewrite")
    @Requires(property = "spec.name", value = SPEC + "-client")
    static class OriginRewritingFilter {
        @RequestFilter
        void rewrite(MutableHttpRequest<?> request) {
            URI uri = request.getUri();
            String host = "localhost".equalsIgnoreCase(uri.getHost()) ? "127.0.0.1" : "localhost";
            request.uri(URI.create(uri.getScheme() + "://" + host + ":" + uri.getPort() + "/cap-paths/stream"));
        }
    }
}
