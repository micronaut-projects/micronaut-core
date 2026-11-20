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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load balancing, retries and circuit breakers.
 */
class ResilienceTest extends GatewayTestBase {

    static final String SPEC = "ResilienceTest";

    EmbeddedServer upstream2;
    int deadPort;

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    @BeforeAll
    void startServers() throws IOException {
        upstream2 = Upstream.start();
        deadPort = SocketUtils.findAvailableTcpPort();
        super.startServers();
    }

    @AfterAll
    void stopSecondUpstream() {
        upstream2.getApplicationContext().close();
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String one = "${test.upstream}";
        String two = upstream2.getURI().toString();
        String dead = "http://127.0.0.1:" + deadPort;
        p.put("micronaut.http.services.orders.urls", List.of(one, two));
        p.put("micronaut.http.services.halfdead.urls", List.of(dead, one));
        p.put("micronaut.http.services.ejecting.urls", List.of(dead, one));
        p.put("micronaut.http.services.ejecting.outlier-detection.enabled", true);
        p.put("micronaut.http.services.ejecting.outlier-detection.consecutive-failures", 2);
        p.put("micronaut.http.services.ejecting.outlier-detection.base-ejection-time", "1m");
        p.put("micronaut.http.services.alldead.urls", List.of(dead, "http://127.0.0.1:" + SocketUtils.findAvailableTcpPort()));
        p.put("micronaut.http.services.alldead.outlier-detection.enabled", true);
        p.put("micronaut.http.services.alldead.outlier-detection.consecutive-failures", 1);
        p.put("micronaut.http.services.secure.urls", List.of(upstream.getURI().toString().replace("http://", "https://")));
        p.put("micronaut.http.services.ipv6.urls", List.of("http://[::1]:" + upstream2.getPort() + "/inspect"));
        p.put("micronaut.http.services.stale.urls", List.of("${test.scripted}"));
        p.put("micronaut.http.services.sticky.urls", List.of(dead, one));
        p.put("micronaut.http.services.released.urls", List.of(dead, one));
        p.put("micronaut.http.services.keyed.urls", List.of(one));
        p.put("micronaut.http.services.keyed.load-balancer-key-header", "X-Session");
        p.put("micronaut.retry.circuit-breakers.recovering.reset", "1s");
        p.put("micronaut.retry.circuit-breakers.windowed.request-volume-threshold", 3);
        p.put("micronaut.retry.circuit-breakers.windowed.failure-ratio", 1);
        p.put("micronaut.retry.circuit-breakers.windowed.reset", "1m");
        p.put("micronaut.retry.circuit-breakers.shared.reset", "1m");
        p.put("micronaut.server.cors.enabled", true);
        p.put("micronaut.server.cors.configurations.web.allowed-origins", List.of("https://app.example"));
        p.put("micronaut.server.idle-timeout", "8s");
        return p;
    }

    private int instancePort(RawHttp.Response response) {
        assertEquals(200, response.status(), response.toString());
        return Integer.parseInt(response.text());
    }

    @Test
    void lb01AndLb04ARouteToAServiceBalancesItsInstancesAndKeepsThePath() throws IOException {
        Map<Integer, AtomicInteger> hits = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            hits.computeIfAbsent(instancePort(get("/orders/up/instance")), k -> new AtomicInteger()).incrementAndGet();
        }
        assertEquals(2, hits.size(), hits.toString());
        assertEquals(5, hits.get(upstream.getPort()).get(), hits.toString());
        assertEquals(5, hits.get(upstream2.getPort()).get(), hits.toString());
        RawHttp.Response inspected = get("/orders/inspect/a%20b?x=%2B+");
        assertEquals("/inspect/a%20b", inspected.inspected("path"));
        assertEquals("x=%2B+", inspected.inspected("query"));
    }

    @Test
    void lb02AServiceWithoutInstancesIs503() throws IOException {
        assertEquals(503, get("/unknown-service/x").status());
    }

    @Test
    void lb03TheSchemeFollowsTheInstance() throws IOException {
        // a https instance on a plain port: the TLS handshake fails, so https was used
        assertEquals(502, get("/secure/up/instance").status());
    }

    @Test
    void lb09AFailingInstanceIsEjectedByTheOutlierDetection() throws IOException {
        for (int i = 0; i < 4; i++) {
            get("/ejecting/up/instance");
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(upstream.getPort(), instancePort(get("/ejecting/up/instance")), "the failing instance was not ejected");
        }
    }

    @Test
    void lb12AllInstancesUnhealthyIsAGatewayError() throws IOException {
        for (int i = 0; i < 4; i++) {
            int status = get("/alldead/x").status();
            assertTrue(status == 502 || status == 503, "status " + status);
        }
    }

    @Test
    void lb15AStaticInstanceListWithIpv6AndAPath() throws IOException {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(InetAddress.getByName("::1"), upstream2.getPort()), 1000);
        } catch (IOException e) {
            Assumptions.abort("No IPv6 loopback: " + e);
        }
        RawHttp.Response response = get("/ipv6/items");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/items", response.inspected("path"));
    }

    @Test
    void ry01AGetIsRetriedOnARetryableStatusAndTheLastStatusIsRelayed() throws IOException {
        RawHttp.Response retried = get("/retry/flaky?key=ry01&failures=2");
        assertEquals(200, retried.status(), retried.toString());
        assertTrue(retried.text().startsWith("attempt 3"), retried.toString());
        RawHttp.Response exhausted = get("/retry/flaky?key=ry01-exhausted&failures=10");
        assertEquals(503, exhausted.status(), exhausted.toString());
        assertEquals("attempt 3", exhausted.text());
        assertEquals(3, counters().get("flaky-ry01-exhausted"));
    }

    @Test
    void ry02APostIsNotRetriedOnAStatus() throws IOException {
        RawHttp.Response response = send("POST", "/retry/flaky?key=ry02&failures=1", "body", "Content-Type: text/plain");
        assertEquals(503, response.status());
        assertEquals(1, counters().get("flaky-ry02"));
    }

    @Test
    void ry03AndRy10AnUnprocessedRequestIsRetriedForAnyMethodOnAnotherInstance() throws IOException {
        for (int i = 0; i < 4; i++) {
            RawHttp.Response post = send("POST", "/halfdead/up/upload", "payload-" + i, "Content-Type: text/plain");
            assertEquals(200, post.status(), post.toString());
            assertEquals("POST " + Upstream.digest(("payload-" + i).getBytes()), post.text());
            RawHttp.Response bodyless = send("POST", "/halfdead/inspect/x", null);
            assertEquals(200, bodyless.status(), bodyless.toString());
            assertEquals("POST", bodyless.inspected("method"));
        }
    }

    @Test
    void ry10TheRetryLeavesOutTheInstancesItTried() throws IOException {
        URI dead = URI.create("http://127.0.0.1:" + deadPort);
        URI alive = upstream.getURI();
        // selects the dead instance twice for every alive one: only leaving out a tried instance reaches the alive one
        AtomicInteger selections = new AtomicInteger();
        LoadBalancer sticky = discriminator -> Publishers.just(ServiceInstance.of("sticky", selections.getAndIncrement() % 3 == 2 ? alive : dead));
        gateway.getApplicationContext().getBean(ProxyHandlerFactory.class).loadBalancer("sticky", sticky);
        for (int i = 0; i < 3; i++) {
            assertEquals(upstream.getPort(), instancePort(get("/sticky/up/instance")));
        }
    }

    @Test
    void ry10TheSelectionsTheRetryDoesNotSendAreReleased() throws IOException {
        URI dead = URI.create("http://127.0.0.1:" + deadPort);
        URI alive = upstream.getURI();
        AtomicInteger selections = new AtomicInteger();
        List<String> reports = new java.util.concurrent.CopyOnWriteArrayList<>();
        LoadBalancer recording = new LoadBalancer() {
            @Override
            public org.reactivestreams.Publisher<ServiceInstance> select(Object discriminator) {
                return Publishers.just(ServiceInstance.of("released", selections.getAndIncrement() % 3 == 2 ? alive : dead));
            }

            @Override
            public void report(ServiceInstance serviceInstance, Outcome outcome) {
                reports.add(serviceInstance.getURI().getPort() + " " + outcome);
            }
        };
        gateway.getApplicationContext().getBean(ProxyHandlerFactory.class).loadBalancer("released", recording);
        // the dead instance, again the dead instance, which was tried and is released, then the alive one
        assertEquals(upstream.getPort(), instancePort(get("/released/up/instance")));
        assertEquals(List.of(deadPort + " CANCELLED"), reports);
    }

    @Test
    void lb16TheLoadBalancerSelectsWithTheKeyOfTheRequest() throws IOException {
        List<Object> keys = new java.util.concurrent.CopyOnWriteArrayList<>();
        LoadBalancer keyed = discriminator -> {
            keys.add(String.valueOf(io.micronaut.http.client.loadbalance.LoadBalancerKey.of(discriminator)));
            return Publishers.just(ServiceInstance.of("keyed", upstream.getURI()));
        };
        gateway.getApplicationContext().getBean(ProxyHandlerFactory.class).loadBalancer("keyed", keyed);
        assertEquals(upstream.getPort(), instancePort(get("/keyed/up/instance", "X-Session: session-1")));
        assertEquals(List.of("session-1"), keys);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void ry17AStreamingBodyThatAnAttemptTookIsNotRetriedWithoutIt() throws IOException {
        String part = "streamed-payload";
        String payload = part + part;
        RawHttp.Response response;
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            // the rest of the body arrives after the attempt took it: the body streams
            http.send("POST /retry-post/flaky?key=ry17&failures=1 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n"
                + Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
            sleep(300);
            http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n0\r\n\r\n");
            response = http.read(false);
        }
        if (response.status() == 200) {
            // the body was sent again: all of it
            assertEquals("attempt 2 " + Upstream.digest(payload.getBytes()), response.text(), response.toString());
        } else {
            // the body could not be sent again: the response of the only attempt
            assertEquals(503, response.status(), response.toString());
            assertEquals(1, counters().get("flaky-ry17"));
        }
    }

    @Test
    void ry04AStaleConnectionIsRetriedForAnIdempotentMethodOnly() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean();
        scripted.on("/stale", (request, in, out, socket) -> {
            request.readBody(in);
            if (armed.compareAndSet(true, false)) {
                // answer and keep the connection, as if idle
                ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nKeep-Alive: timeout=60\r\n\r\nfirst");
                // the next request on this connection finds it closed
                ScriptedUpstream.readHead(in);
                return false;
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\nsecond");
            return true;
        });
        armed.set(true);
        assertEquals("first", get("/stale/stale").text());
        RawHttp.Response retried = get("/stale/stale");
        assertEquals(200, retried.status(), retried.toString());
        assertEquals("second", retried.text());
        armed.set(true);
        assertEquals("first", send("POST", "/stale/stale", "a").text());
        RawHttp.Response post = send("POST", "/stale/stale", "b");
        assertEquals(502, post.status(), "a POST sent on a connection that closed was retried: " + post);
    }

    @Test
    void ry05NothingIsRetriedAfterTheHeaders() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        scripted.on("/headers-then-reset", (request, in, out, socket) -> {
            attempts.incrementAndGet();
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\npartial");
            Thread.sleep(200);
            socket.setSoLinger(true, 0);
            return false;
        });
        RawHttp.Response response = get("/retry-raw/headers-then-reset");
        assertEquals(200, response.status());
        assertFalse(response.complete());
        assertEquals(1, attempts.get());
    }

    @Test
    void ry06AResetBeforeTheHeadersIsRetried() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        scripted.on("/reset-once", (request, in, out, socket) -> {
            if (attempts.incrementAndGet() == 1) {
                socket.setSoLinger(true, 0);
                return false;
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response response = get("/retry-raw/reset-once");
        assertEquals("ok", response.text(), response.toString());
        assertEquals(2, attempts.get());
    }

    @Test
    void ry13AndRy16TheHeadersAndThePathAreTheSameOnEveryAttempt() throws IOException {
        RawHttp.Response response = get("/retry/flaky?key=ry13&failures=2&describe", "X-Custom: once");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/flaky", response.inspected("path"));
        assertEquals(List.of("once"), response.inspected().get("h:x-custom"));
        assertEquals(List.of("gw"), response.inspected().get("h:x-added"));
        assertEquals(List.of("127.0.0.1"), response.inspected().get("h:x-forwarded-for"));
    }

    @Test
    void cb01AHealthyRouteWithABreakerRelaysTheResponse() throws IOException {
        assertEquals("/inspect/x", get("/cb-healthy/inspect/x").inspected("path"));
    }

    @Test
    void cb02AndCb03AnOpenCircuitAnswersWithTheFallbackOrA503() throws IOException {
        // the first failure opens the circuit
        RawHttp.Response failed = get("/cb-fallback/x/seg-1");
        assertEquals(200, failed.status(), failed.toString());
        assertTrue(failed.text().startsWith("fallback seg-1 "), failed.toString());
        assertTrue(failed.text().contains("Connect"), failed.toString());
        RawHttp.Response open = get("/cb-fallback/x/seg-2");
        assertTrue(open.text().startsWith("fallback seg-2 CircuitOpenException"), open.toString());
        // without a fallback
        assertEquals(502, get("/cb-nofallback/x").status());
        RawHttp.Response openNoFallback = get("/cb-nofallback/x");
        assertEquals(503, openNoFallback.status());
        assertTrue(openNoFallback.header("Content-Type").startsWith("application/json"));
        assertEquals(CircuitState.OPEN, gateway.getApplicationContext().getBean(CircuitBreakerRegistry.class).findState("nofallback").orElseThrow());
    }

    @Test
    void cb03AFallbackReadsTheBodyThatNoAttemptSent() throws IOException {
        // the first request opens the circuit: its attempt took the body, which the fallback cannot read
        RawHttp.Response first = send("POST", "/cb-body/x", "first", "Content-Type: text/plain");
        assertEquals(200, first.status(), first.toString());
        assertTrue(first.text().startsWith("body "), first.toString());
        assertFalse(first.text().equals("body first"), first.toString());
        // the circuit is open: no attempt takes the body, the fallback reads it
        RawHttp.Response second = send("POST", "/cb-body/x", "second", "Content-Type: text/plain");
        assertEquals("body second", second.text(), second.toString());
    }

    @Test
    void cb11AFallbackStatusThatHttpStatusDoesNotNameIsAnsweredByTheFallback() throws IOException {
        RawHttp.Response response = get("/cb-599/up/status/599");
        assertEquals(200, response.status(), response.toString());
        assertTrue(response.text().startsWith("fallback"), response.toString());
    }

    @Test
    void cb12AFallbackIsNotGivenMoreOfAReplayedBodyThanItsLimit() throws IOException {
        String part = "01234";
        RawHttp.Response response;
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            // the rest of the body arrives after the attempt took it: the body streams, and the
            // retries keep it up to their limit, more than the limit of the fallback
            http.send("POST /cb-body-limit/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n"
                + Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
            sleep(300);
            http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n0\r\n\r\n");
            response = http.read(false);
        }
        assertEquals(200, response.status(), response.toString());
        assertTrue(response.text().startsWith("body "), response.toString());
        assertNotEquals("body " + part + part, response.text(), "the fallback was given more than its 4 bytes");
    }

    @Test
    void cb03AFallbackReadsTheEmptyBodyOfARequestWithoutOne() throws IOException {
        RawHttp.Response response = get("/cb-body-empty/x");
        assertEquals("body ", response.text(), response.toString());
    }

    @Test
    void cb04And08A5xxResponseCountsAsAFailureAndTheFallbackHasNoUpstreamHeader() throws IOException {
        RawHttp.Response error = get("/cb-status/up/status/500");
        assertEquals(500, error.status());
        assertEquals("yes", error.header("X-Upstream"));
        RawHttp.Response fallback = get("/cb-status/up/status/200");
        assertEquals(200, fallback.status());
        assertTrue(fallback.text().startsWith("fallback"), fallback.toString());
        assertNull(fallback.header("X-Upstream"));
    }

    @Test
    void cb04OnlyTheConfiguredStatusesCountAsFailures() throws IOException {
        // 500 is not a failure of this route: the circuit stays closed
        for (int i = 0; i < 3; i++) {
            assertEquals(500, get("/cb-statuses/up/status/500").status());
        }
        assertEquals(CircuitState.CLOSED, circuitBreakers().findState("statuses").orElseThrow());
        assertEquals(503, get("/cb-statuses/up/status/503").status());
        assertEquals(CircuitState.OPEN, circuitBreakers().findState("statuses").orElseThrow());
        RawHttp.Response open = get("/cb-statuses/up/status/200");
        assertEquals(503, open.status(), "an open circuit without a fallback: " + open);
    }

    @Test
    void cb08AFallbackStatusIsAnsweredByTheFallbackWithoutTheHeadersOfTheUpstream() throws IOException {
        RawHttp.Response response = get("/cb-fallback-status/up/status/503");
        assertEquals(200, response.status(), response.toString());
        assertTrue(response.text().startsWith("fallback - UpstreamStatusFailure"), response.toString());
        assertNull(response.header("X-Upstream"));
        // another 5xx is relayed; only 504 counts for this circuit, which stays closed
        RawHttp.Response relayed = get("/cb-fallback-status/up/status/500");
        assertEquals(500, relayed.status());
        assertEquals("yes", relayed.header("X-Upstream"));
    }

    @Test
    void cb02AWindowedCircuitOpensOnTheRatioOfItsWindowAndThenAnswers503() throws IOException {
        assertEquals(502, get("/cb-windowed/x").status());
        assertEquals(CircuitState.CLOSED, circuitBreakers().findState("windowed").orElseThrow(), "the window of three is not full");
        assertEquals(502, get("/cb-windowed/x").status());
        assertEquals(502, get("/cb-windowed/x").status());
        assertEquals(CircuitState.OPEN, circuitBreakers().findState("windowed").orElseThrow());
        var snapshot = circuitBreakers().findSnapshot("windowed").orElseThrow();
        assertEquals(3, snapshot.requestVolumeThreshold());
        assertEquals(1, snapshot.openedCount());
        assertEquals(503, get("/cb-windowed/x").status());
    }

    private CircuitBreakerRegistry circuitBreakers() {
        return gateway.getApplicationContext().getBean(CircuitBreakerRegistry.class);
    }

    @Test
    void cb09TheFallbackResponsePassesTheRouteFiltersAndCors() throws IOException {
        get("/cb-filtered/x");
        RawHttp.Response fallback = get("/cb-filtered/x", "Origin: https://app.example");
        assertTrue(fallback.text().startsWith("fallback"), fallback.toString());
        assertEquals("DENY", fallback.header("X-Frame-Options"));
        assertEquals("https://app.example", fallback.header("Access-Control-Allow-Origin"));
    }

    @Test
    void cb10AnInvalidFallbackFailsTheStartup() {
        for (String fallback : List.of("noSuchHandler", "forward:/fallback")) {
            Map<String, Object> properties = Map.of(
                "spec.name", SPEC + "-invalid",
                "micronaut.server.port", -1,
                "micronaut.gateway.routes.bad.uri", "http://localhost:1",
                "micronaut.gateway.routes.bad.predicates", List.of("Path=/bad/**"),
                "micronaut.gateway.routes.bad.filters", List.of("CircuitBreaker=bad, " + fallback));
            Exception failure = assertThrows(Exception.class, () -> ApplicationContext.run(EmbeddedServer.class, properties).close());
            assertTrue(RoutingTest.rootMessage(failure).contains("fallback"), RoutingTest.rootMessage(failure));
        }
    }

    @Test
    void aCircuitBreakerIsSharedWithAnnotatedMethodsOfTheSameName() throws IOException {
        SharedService service = gateway.getApplicationContext().getBean(SharedService.class);
        assertThrows(IllegalStateException.class, service::fail);
        RawHttp.Response response = get("/cb-shared/inspect/x");
        assertEquals(503, response.status(), "the route shares the open circuit of the method: " + response);
    }

    @Test
    void aHalfOpenCircuitClosesOnASuccess() throws Exception {
        RawHttp.Response failure = get("/cb-recovering/up/status/503");
        assertEquals(503, failure.status());
        assertEquals(503, get("/cb-recovering/up/status/200").status(), "the circuit is open");
        Thread.sleep(1500);
        assertEquals(200, get("/cb-recovering/up/status/200").status(), "the half-open circuit let the request through");
        assertEquals(200, get("/cb-recovering/up/status/200").status());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class SharedService {
        @CircuitBreaker(name = "shared", attempts = "1", delay = "1ms")
        void fail() {
            throw new IllegalStateException("shared down");
        }
    }

    @Singleton
    @Named("echoFallback")
    @Requires(property = "spec.name", value = SPEC)
    static class EchoFallback implements AsyncRequestHandler {
        @Override
        public CompletableFuture<HttpResponse<?>> handle(io.micronaut.http.HttpRequest<?> request, io.micronaut.http.PathVariables pathVariables) {
            Throwable failure = GatewayRequestContext.find(request).flatMap(GatewayRequestContext::failure).orElse(null);
            String segment = pathVariables.contains("segment") ? pathVariables.getString("segment") : "-";
            Throwable root = failure;
            while (root != null && root.getCause() != null && !(root instanceof io.micronaut.retry.exception.CircuitOpenException)) {
                root = root.getCause();
            }
            String text = "fallback " + segment + " " + (root == null ? "none" : root.getClass().getSimpleName() + ": " + root.getMessage());
            return CompletableFuture.completedFuture(HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    /**
     * Answers with the body of the request, or the failure to read it.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class BodyFallback implements AsyncRequestHandler {
        @Override
        public CompletableFuture<HttpResponse<?>> handle(io.micronaut.http.HttpRequest<?> request, io.micronaut.http.PathVariables pathVariables) {
            CompletableFuture<HttpResponse<?>> response = new CompletableFuture<>();
            try {
                ((io.micronaut.http.ServerHttpRequest<?>) request).byteBody().buffer().whenComplete((body, error) -> {
                    String text = error == null ? body.toString(java.nio.charset.StandardCharsets.UTF_8) : "unreadable " + error.getClass().getSimpleName();
                    response.complete(HttpResponse.ok("body " + text).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
            } catch (RuntimeException e) {
                response.complete(HttpResponse.ok("body unreadable " + e.getClass().getSimpleName()).contentType(MediaType.TEXT_PLAIN_TYPE));
            }
            return response;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final int deadPort;
        private final EchoFallback fallback;
        private final BodyFallback bodyFallback;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted, EchoFallback fallback,
               BodyFallback bodyFallback, @Value("${micronaut.http.services.halfdead.urls[0]}") String dead) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.fallback = fallback;
            this.bodyFallback = bodyFallback;
            this.deadPort = URI.create(dead).getPort();
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            GatewayRetry retry = GatewayRetry.of(2).withBackoff(Duration.ofMillis(1), 1);
            gateway.route("orders").path("/orders/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://orders");
            gateway.route("unknown-service").path("/unknown-service/**").uri("lb://unknown-service");
            gateway.route("secure").path("/secure/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://secure");
            gateway.route("ejecting").path("/ejecting/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://ejecting");
            gateway.route("alldead").path("/alldead/**").uri("lb://alldead");
            gateway.route("ipv6").path("/ipv6/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://ipv6");
            gateway.route("halfdead").path("/halfdead/**").filters(GatewayFilters.stripPrefix(1)).retry(retry).uri("lb://halfdead");
            gateway.route("sticky").path("/sticky/**").filters(GatewayFilters.stripPrefix(1)).retry(retry).uri("lb://sticky");
            gateway.route("released").path("/released/**").filters(GatewayFilters.stripPrefix(1)).retry(retry).uri("lb://released");
            gateway.route("keyed").path("/keyed/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://keyed");
            gateway.route("retry-post").path("/retry-post/**").filters(GatewayFilters.stripPrefix(1))
                .retry(retry.withStatuses(503).withMethods("POST")).uri(upstream);
            gateway.route("stale").path("/stale/**").filters(GatewayFilters.stripPrefix(1)).retry(retry).uri("lb://stale");
            gateway.route("retry").path("/retry/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Added", "gw"))
                .retry(retry.withStatuses(503))
                .uri(upstream);
            gateway.route("retry-raw").path("/retry-raw/**").filters(GatewayFilters.stripPrefix(1)).retry(retry).uri(scripted);
            gateway.route("cb-healthy").path("/cb-healthy/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("healthy", fallback)).uri(upstream);
            gateway.route("cb-fallback").path("/cb-fallback/x/{segment}")
                .circuitBreaker(GatewayCircuitBreaker.of("fallback", fallback)).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-nofallback").path("/cb-nofallback/**")
                .circuitBreaker(GatewayCircuitBreaker.of("nofallback")).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-status").path("/cb-status/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("status", fallback)).uri(upstream);
            gateway.route("cb-filtered").path("/cb-filtered/**").filters(GatewayFilters.secureHeaders())
                .circuitBreaker(GatewayCircuitBreaker.of("filtered", fallback)).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-shared").path("/cb-shared/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("shared")).uri(upstream);
            gateway.route("cb-statuses").path("/cb-statuses/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("statuses").withFailureStatuses(503)).uri(upstream);
            gateway.route("cb-fallback-status").path("/cb-fallback-status/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("fallback-status", fallback).withFallbackStatuses(503).withFailureStatuses(504)).uri(upstream);
            gateway.route("cb-windowed").path("/cb-windowed/**")
                .circuitBreaker(GatewayCircuitBreaker.of("windowed")).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-body").path("/cb-body/**")
                .circuitBreaker(GatewayCircuitBreaker.of("body", bodyFallback)).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-599").path("/cb-599/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("s599", fallback).withFallbackStatuses(599)).uri(upstream);
            gateway.route("cb-body-limit").path("/cb-body-limit/**").retry(retry.withMethods("POST"))
                .circuitBreaker(GatewayCircuitBreaker.of("body-limit", bodyFallback).withMaxFallbackBodyBytes(4)).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-body-empty").path("/cb-body-empty/**")
                .circuitBreaker(GatewayCircuitBreaker.of("body-empty", bodyFallback)).uri("http://127.0.0.1:" + deadPort);
            gateway.route("cb-recovering").path("/cb-recovering/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("recovering")).uri(upstream);
        }
    }
}
