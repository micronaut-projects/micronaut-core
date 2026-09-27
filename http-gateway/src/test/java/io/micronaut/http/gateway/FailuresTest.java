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
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Failures and timeouts: how a request that cannot be relayed is answered.
 *
 * <ul>
 *     <li>502: connection refused, name not resolved, TLS failure, reset or close before the
 *     headers, a malformed response;</li>
 *     <li>504: the connection or the headers did not arrive in time;</li>
 *     <li>503: no instance of the service, or an open circuit without a fallback;</li>
 *     <li>after the headers: the client connection is cut, never completed.</li>
 * </ul>
 */
class FailuresTest extends GatewayTestBase {

    static final String SPEC = "FailuresTest";
    static final String BLACKHOLE = "10.255.255.1";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("test.closed-port", SocketUtils.findAvailableTcpPort());
        p.put("micronaut.http.services.blackhole.url", "http://" + BLACKHOLE + ":81");
        p.put("micronaut.http.services.blackhole.connect-timeout", "300ms");
        p.put("micronaut.http.services.slowbody.url", "${test.scripted}");
        p.put("micronaut.http.services.slowbody.read-timeout", "1s");
        p.put("micronaut.server.idle-timeout", "8s");
        return p;
    }

    @Test
    void er01ARefusedConnectionIsA502WithAJsonError() throws IOException {
        RawHttp.Response response = get("/refused/x");
        assertEquals(502, response.status(), response.toString());
        // ER-09: the error of the gateway is the error of the server
        assertTrue(response.header("Content-Type").startsWith("application/json"), response.toString());
        assertTrue(response.text().contains("\"message\""), response.toString());
    }

    @Test
    void er01bTheErrorOfAFailedUpstreamDoesNotNameItsAddress() throws IOException {
        RawHttp.Response response = get("/refused/x");
        assertEquals(502, response.status(), response.toString());
        assertFalse(response.text().contains("127.0.0.1"), response.toString());
        assertFalse(response.text().toLowerCase(java.util.Locale.ROOT).contains("refused:"), response.toString());
    }

    @Test
    void er03AHostThatDoesNotResolveIsA502() throws IOException {
        assertEquals(502, get("/unresolved/x").status());
    }

    @Test
    void er04ACloseOrAResetBeforeTheHeadersIsA502() throws Exception {
        scripted.on("/close-before", (request, in, out, socket) -> false);
        scripted.on("/reset-before", (request, in, out, socket) -> {
            socket.setSoLinger(true, 0);
            return false;
        });
        assertEquals(502, get("/raw/close-before").status());
        assertEquals(502, get("/raw/reset-before").status());
    }

    @Test
    void er06AMalformedChunkMidStreamCutsTheResponse() throws Exception {
        scripted.on("/bad-chunk", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\nzz\r\nnope\r\n");
            Thread.sleep(200);
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /raw/bad-chunk HTTP/1.1\r\nHost: localhost\r\n\r\n");
            String text = new String(http.readToEnd(), StandardCharsets.ISO_8859_1);
            assertTrue(text.startsWith("HTTP/1.1 200"), text);
            assertFalse(text.endsWith("0\r\n\r\n"), text);
            assertFalse(text.contains("nope"), text);
        }
    }

    @Test
    void er07ATlsFailureToTheUpstreamIsA502() throws IOException {
        assertEquals(502, get("/tls/x").status());
    }

    @Test
    void er10AFilterFailureIsA500AndTheUpstreamResponseIsReleased() throws IOException {
        assertEquals(500, get("/failing-request-filter/x").status());
        assertEquals(500, get("/failing-response-filter/up/stream?size=100000").status());
    }

    @Test
    void er13ErrorRoutesSeeTheRootCause() throws IOException {
        RawHttp.Response response = get("/grouped-errors/x");
        assertEquals(502, response.status(), response.toString());
        assertEquals("refused", response.header("X-Route"));
        assertTrue(response.header("X-Cause").contains("Connect"), response.toString());
    }

    @Test
    void to01AndTo03TheResponseTimeoutOfARouteIsA504() throws IOException {
        long start = System.nanoTime();
        RawHttp.Response response = get("/timeout/up/slow?ms=3000");
        assertEquals(504, response.status(), response.toString());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500, "the route timeout did not apply");
        // a route without its own timeout waits for the read timeout of the client
        assertEquals("slow", get("/up/slow?ms=700").text());
    }

    @Test
    void to02AConnectTimeoutIsA504() throws IOException {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(BLACKHOLE, 81), 300);
            Assumptions.abort("The address is not a black hole here");
        } catch (SocketTimeoutException e) {
            // a black hole: the connection times out
        } catch (IOException e) {
            Assumptions.abort("The address is unreachable here, not a black hole: " + e);
        }
        long start = System.nanoTime();
        RawHttp.Response response = get("/blackhole/x");
        assertEquals(504, response.status(), response.toString());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 5000);
    }

    @Test
    void to04TheResponseTimeoutDoesNotCutAStreamAfterTheHeaders() throws IOException {
        RawHttp.Response events = get("/timeout/up/sse?count=4&delay=400", "Accept: text/event-stream");
        assertEquals(200, events.status(), events.toString());
        assertTrue(events.complete());
        assertTrue(events.text().contains("event 4"), events.toString());
    }

    @Test
    void to05AReadTimeoutAfterTheHeadersCutsTheResponse() throws Exception {
        scripted.on("/stall", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\npartial");
            Thread.sleep(4000);
            return false;
        });
        RawHttp.Response response = get("/slowbody/stall");
        assertEquals(200, response.status());
        assertFalse(response.complete(), response.toString());
    }

    @Test
    void to06TheResponseTimeoutStartsOnceTheRequestIsSent() throws Exception {
        scripted.on("/slow-upload", (request, in, out, socket) -> {
            byte[] body = request.readBody(in);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + String.valueOf(body.length).length() + "\r\n\r\n" + body.length);
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /timeout-raw/slow-upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nContent-Length: 20\r\n\r\n");
            for (int i = 0; i < 20; i++) {
                http.send("x");
                Thread.sleep(100);
            }
            RawHttp.Response response = http.read(false);
            assertEquals(200, response.status(), "the timeout counted the time of the upload: " + response);
            assertEquals("20", response.text());
        }
    }

    @Test
    void locationOfTheUpstreamIsRewrittenToTheGateway() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /location/up/location HTTP/1.1\r\nHost: gw.example:8080\r\nConnection: close\r\n\r\n");
        assertEquals(201, response.status(), response.toString());
        assertEquals("http://gw.example:8080/up/items/1?x=1", response.header("Location"));
        // another host is kept
        RawHttp.Response redirect = get("/location/up/redirect");
        assertTrue(redirect.header("Location").contains(String.valueOf(gateway.getPort())), redirect.toString());
    }

    @Test
    void trailersAreRelayedBothWays() throws Exception {
        scripted.on("/trailers", (request, in, out, socket) -> {
            byte[] body = request.readBody(in);
            String te = String.valueOf(request.header("te"));
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTrailer: grpc-status\r\n\r\n"
                + Integer.toHexString(body.length + te.length() + 1) + "\r\n" + new String(body, StandardCharsets.US_ASCII) + " " + te + "\r\n0\r\ngrpc-status: 0\r\n\r\n");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/trailers HTTP/1.1\r\nHost: localhost\r\nTE: trailers\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n4\r\nbody\r\n0\r\nx-checksum: 1\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals(200, response.status(), response.toString());
            assertEquals("body trailers", response.text());
            assertEquals("0", response.header("trailer:grpc-status"), response.toString());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final int closedPort;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted, @Value("${test.closed-port}") int closedPort) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.closedPort = closedPort;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("up").path("/up/**").uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("refused").path("/refused/**").uri("http://127.0.0.1:" + closedPort);
            gateway.route("unresolved").path("/unresolved/**").uri("http://no-such-host.invalid");
            gateway.route("tls").path("/tls/**").uri(upstream.replace("http://", "https://"));
            gateway.route("blackhole").path("/blackhole/**").uri("lb://blackhole");
            gateway.route("slowbody").path("/slowbody/**").filters(GatewayFilters.stripPrefix(1)).uri("lb://slowbody");
            gateway.route("timeout").path("/timeout/**").filters(GatewayFilters.stripPrefix(1)).responseTimeout(Duration.ofMillis(500)).uri(upstream);
            gateway.route("timeout-raw").path("/timeout-raw/**").filters(GatewayFilters.stripPrefix(1)).responseTimeout(Duration.ofMillis(1000)).uri(scripted);
            gateway.route("location").path("/location/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.rewriteLocationResponseHeader())
                .uri(upstream);
            gateway.route("failing-request-filter").path("/failing-request-filter/**").filters(new GatewayFilter() {
                @Override
                public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
                    throw new IllegalStateException("request filter failed");
                }
            }).uri(upstream);
            gateway.route("failing-response-filter").path("/failing-response-filter/**").filters(GatewayFilters.stripPrefix(1), new GatewayFilter() {
                @Override
                public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
                    throw new IllegalStateException("response filter failed");
                }
            }).uri(upstream);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class ErrorRoutes implements HttpRoutes {
        private final ProxyHandlerFactory proxies;
        private final int closedPort;

        ErrorRoutes(ProxyHandlerFactory proxies, @Value("${test.closed-port}") int closedPort) {
            this.proxies = proxies;
            this.closedPort = closedPort;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/grouped-errors", group -> {
                group.error(UpstreamException.class, (request, error) -> {
                    Throwable root = error;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    return HttpResponse.status(((UpstreamException) error).getStatus())
                        .header("X-Route", ((UpstreamException) error).getRouteId())
                        .header("X-Cause", root.getClass().getSimpleName());
                });
                TestProxyRoutes.proxy(group, "/", proxies.create(GatewayRoute.builder("refused").uri("http://127.0.0.1:" + closedPort).build()));
            });
        }
    }

}
