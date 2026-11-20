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
package io.micronaut.http.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The response cache keyed by the authority and the revision of the route, behind the guards of
 * the route; the CORS policy of a route over the one of the upstream; the body filters on the
 * responses they can rewrite only; a refresh that retires a route while it streams; and the
 * disposal of a raw client.
 */
class EdgeHardeningTest extends GatewayTestBase {

    static final String SPEC = "EdgeHardeningTest";
    static final RecordingStore STORE = new RecordingStore();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "10s");
        p.put("micronaut.gateway.routes.cors-method.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.cors-method.predicates", List.of("Path=/cors-method/**", "Method=GET"));
        p.put("micronaut.gateway.routes.cors-method.filters", List.of("StripPrefix=1", "Cors=https://app.example"));
        return p;
    }

    private RawHttp.Response withHost(String method, String path, String host, String... headers) throws IOException {
        StringBuilder request = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\nHost: ").append(host).append("\r\nConnection: close\r\n");
        for (String header : headers) {
            request.append(header).append("\r\n");
        }
        if (!method.equals("GET")) {
            request.append("Content-Length: 0\r\n");
        }
        request.append("\r\n");
        return RawHttp.exchange(gateway.getPort(), request.toString().getBytes(StandardCharsets.US_ASCII), false);
    }

    @Test
    void theCacheKeepsTheResponsesOfTwoPortsOfAHostApart() throws IOException {
        RawHttp.Response first = withHost("GET", "/cache-host/inspect/tenant", "tenant.example:1111");
        RawHttp.Response second = withHost("GET", "/cache-host/inspect/tenant", "tenant.example:2222");
        assertEquals("tenant.example:1111", first.inspected("h:host"));
        assertEquals("tenant.example:2222", second.inspected("h:host"), "the response of the other port was served");
    }

    @Test
    void aCacheHitStillGoesThroughTheRateLimitOfTheRoute() throws IOException {
        RawHttp.Response first = get("/cache-limited/inspect/limited", "X-User: u");
        assertEquals(200, first.status(), first.toString());
        RawHttp.Response second = get("/cache-limited/inspect/limited", "X-User: u");
        assertEquals(429, second.status(), "the hit skipped the rate limiter: " + second);
    }

    @Test
    void aWriteThatTheUpstreamRefusesKeepsTheCachedResponses() throws IOException {
        RawHttp.Response stored = get("/cache-write/up/status/200");
        assertEquals(200, stored.status());
        RawHttp.Response refused = send("POST", "/cache-write/up/status/200", "");
        assertTrue(refused.status() >= 400, refused.toString());
        RawHttp.Response hit = get("/cache-write/up/status/200");
        assertNotNull(hit.header("Age"), "the refused write purged the cache: " + hit);
    }

    @Test
    void theStoreSeesNoCredentialsOfTheVariant() throws IOException {
        scripted.on("/vary-cookie", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nCache-Control: public, max-age=60\r\nVary: Cookie\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        get("/cache-store/vary-cookie", "Cookie: session=s3cr3t-value");
        get("/cache-store/vary-cookie", "Cookie: session=s3cr3t-value");
        assertFalse(STORE.keys.isEmpty());
        for (String key : STORE.keys) {
            assertFalse(key.contains("s3cr3t"), key);
        }
    }

    @Test
    void theCorsHeadersOfTheUpstreamAreNotRelayedOnARouteWithAPolicy() throws IOException {
        scripted.on("/grants", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Credentials: true\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response response = withHost("GET", "/cors-upstream/grants", "gw.example", "Origin: https://app.example");
        assertEquals(200, response.status(), response.toString());
        assertEquals("https://app.example", response.header("Access-Control-Allow-Origin"));
        assertNull(response.header("Access-Control-Allow-Credentials"), response.toString());
    }

    @Test
    void aPreflightForTheMethodOfAMethodPredicateGetsThePolicyOfTheRoute() throws IOException {
        RawHttp.Response preflight = RawHttp.exchange(gateway.getPort(), ("OPTIONS /cors-method/inspect/x HTTP/1.1\r\nHost: gw.example\r\nOrigin: https://app.example"
            + "\r\nAccess-Control-Request-Method: GET\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII), false);
        assertEquals(200, preflight.status(), preflight.toString());
        assertEquals("https://app.example", preflight.header("Access-Control-Allow-Origin"));
    }

    @Test
    void theJsonFilterRelaysOtherBodiesAndPartsAsTheyAreAndDropsTheValidatorsOfWhatItRewrites() throws IOException {
        scripted.on("/html-error", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/html\r\nContent-Length: 13\r\n\r\n<h1>oops</h1>");
            return true;
        });
        scripted.on("/part", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 206 Partial Content\r\nContent-Type: application/json\r\nContent-Range: bytes 0-6/20\r\nContent-Length: 7\r\n\r\n{\"a\":1,");
            return true;
        });
        scripted.on("/json", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nETag: \"v1\"\r\nAccept-Ranges: bytes\r\nContent-Length: 18\r\n\r\n{\"a\":1,\"secret\":2}");
            return true;
        });
        RawHttp.Response html = get("/json-filter/html-error");
        assertEquals(500, html.status(), html.toString());
        assertEquals("<h1>oops</h1>", html.text());
        RawHttp.Response part = get("/json-filter/part");
        assertEquals(206, part.status(), part.toString());
        assertEquals("{\"a\":1,", part.text());
        RawHttp.Response json = get("/json-filter/json");
        assertEquals(200, json.status(), json.toString());
        assertEquals("{\"a\":1}", json.text());
        assertNull(json.header("ETag"));
        assertNull(json.header("Accept-Ranges"));
    }

    @Test
    void aLoadBalancerThatThrowsFailsTheRequestInsteadOfHangingIt() throws IOException {
        RawHttp.Response response = get("/throwing-lb/x");
        assertTrue(response.status() >= 500, response.toString());
    }

    @Test
    void aRefreshThatRetiresARouteLetsItsStreamFinish() throws Exception {
        GatewayRouteRefresher refresher = gateway.getApplicationContext().getBean(GatewayRouteRefresher.class);
        refresher.retireDelay(Duration.ofMillis(50));
        Locator locator = gateway.getApplicationContext().getBean(Locator.class);
        GatewayRouteRegistry registry = gateway.getApplicationContext().getBean(GatewayRouteRegistry.class);
        locator.routes.clear();
        locator.routes.add(GatewayRoute.builder("streaming").path("/streaming/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted.uri()).build());
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        scripted.on("/slow-stream", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n");
            for (int i = 0; i < 10; i++) {
                ScriptedUpstream.write(out, "1\r\n" + i + "\r\n");
                Thread.sleep(150);
            }
            ScriptedUpstream.write(out, "0\r\n\r\n");
            return false;
        });
        CompletableFuture<RawHttp.Response> streaming = CompletableFuture.supplyAsync(() -> {
            try {
                return get("/streaming/slow-stream");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);
        // the route goes away: its handler is retired while the stream runs
        locator.routes.clear();
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        RawHttp.Response response = streaming.get(15, TimeUnit.SECONDS);
        assertEquals(200, response.status());
        assertEquals("0123456789", response.text(), "the retirement cut the stream");
    }

    @Test
    void aRepointedRouteDoesNotServeTheCachedResponsesOfItsPreviousUpstream() throws Exception {
        Locator locator = gateway.getApplicationContext().getBean(Locator.class);
        GatewayRouteRegistry registry = gateway.getApplicationContext().getBean(GatewayRouteRegistry.class);
        scripted.on("/inspect/repointed", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nCache-Control: max-age=60\r\nContent-Length: 6\r\n\r\nsecond");
            return true;
        });
        locator.routes.clear();
        locator.routes.add(GatewayRoute.builder("cached-dyn").path("/cached-dyn/**")
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.localResponseCache(STORE, Duration.ofMinutes(1), 1024 * 1024)).uri(upstream.getURI()).build());
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(200, get("/cached-dyn/inspect/repointed").status());
        locator.routes.clear();
        locator.routes.add(GatewayRoute.builder("cached-dyn").path("/cached-dyn/**")
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.localResponseCache(STORE, Duration.ofMinutes(1), 1024 * 1024)).uri(scripted.uri()).build());
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals("second", get("/cached-dyn/inspect/repointed").text());
    }

    @Test
    void aDisposedRawClientIsForgottenAndCreatedAgain() throws Exception {
        scripted.on("/disposed", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttpClientRegistry registry = gateway.getApplicationContext().getBean(RawHttpClientRegistry.class);
        HttpVersionSelection version = HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1);
        String id = scripted.uri().toString();
        registry.getAsyncRawClient(version, id, null);
        registry.disposeRawClient(version, id, null);
        // disposed again while it is stopped: still forgotten
        registry.disposeRawClient(version, id, null);
        AsyncRawHttpClient second = registry.getAsyncRawClient(version, id, null);
        io.micronaut.http.HttpResponse<?> response = second.exchange(io.micronaut.http.HttpRequest.GET(scripted.uri().resolve("/disposed").toString()), null,
            io.micronaut.http.client.RawRequestOptions.proxy()).toCompletableFuture().get(10, TimeUnit.SECONDS);
        try {
            assertEquals(200, response.code(), "the registry gave back the stopped client");
        } finally {
            if (response instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final JsonMapper mapper;
        private final ProxyHandlerFactory proxies;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted, JsonMapper mapper, ProxyHandlerFactory proxies) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.mapper = mapper;
            this.proxies = proxies;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("cache-host").path("/cache-host/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.preserveHostHeader(), GatewayFilters.localResponseCache(Duration.ofMinutes(1), 1024 * 1024))
                .uri(upstream);
            // the cache first, as in the default filters: the limiter still runs for a hit
            gateway.route("cache-limited").path("/cache-limited/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.localResponseCache(Duration.ofMinutes(1), 1024 * 1024),
                    GatewayRateLimit.builder(1, 1).keyResolver(KeyResolver.header("X-User")).build())
                .uri(upstream);
            gateway.route("cache-write").path("/cache-write/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.localResponseCache(Duration.ofMinutes(1), 1024 * 1024))
                .uri(upstream);
            gateway.route("cache-store").path("/cache-store/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.localResponseCache(STORE, Duration.ofMinutes(1), 1024 * 1024))
                .uri(scripted);
            gateway.route("cors-upstream").path("/cors-upstream/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.cors(List.of("https://app.example"), List.of("GET"), List.of(), List.of(), false, -1))
                .uri(scripted);
            gateway.route("json-filter").path("/json-filter/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeJsonAttributesResponseBody(mapper, 1024 * 1024, false, "secret"))
                .uri(scripted);
            gateway.route("throwing-lb").path("/throwing-lb/**").uri("lb://throwing");
            proxies.loadBalancer("throwing", new io.micronaut.http.client.LoadBalancer() {
                @Override
                public org.reactivestreams.Publisher<io.micronaut.discovery.ServiceInstance> select(@Nullable Object discriminator) {
                    throw new IllegalStateException("the load balancer failed");
                }
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Locator implements GatewayRouteLocator {
        final List<GatewayRoute> routes = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<List<GatewayRoute>> getRoutes() {
            return CompletableFuture.completedFuture(List.copyOf(routes));
        }
    }

    /**
     * An in-memory store that records the keys it is given.
     */
    static final class RecordingStore implements ResponseCacheStore {
        final List<String> keys = new CopyOnWriteArrayList<>();
        private final Map<String, Map<String, CachedResponse>> entries = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<@Nullable CachedResponse> get(String resource, String variant) {
            keys.add(resource + "|" + variant);
            Map<String, CachedResponse> variants = entries.get(resource);
            return CompletableFuture.completedFuture(variants == null ? null : variants.get(variant));
        }

        @Override
        public CompletionStage<?> put(String resource, String variant, CachedResponse response) {
            keys.add(resource + "|" + variant);
            entries.computeIfAbsent(resource, r -> new ConcurrentHashMap<>()).put(variant, response);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> invalidate(String resource) {
            entries.remove(resource);
            return CompletableFuture.completedFuture(null);
        }
    }
}
