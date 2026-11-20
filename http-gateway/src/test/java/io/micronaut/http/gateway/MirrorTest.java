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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirroring requests to a shadow upstream (MI-01 to MI-07).
 */
class MirrorTest extends GatewayTestBase {

    static final String SPEC = "MirrorTest";

    /**
     * What the shadow received: the path, the Host and the body.
     */
    record Shadowed(String target, String host, String body) {
    }

    private final BlockingQueue<Shadowed> shadowed = new LinkedBlockingQueue<>();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "5s");
        p.put("micronaut.gateway.routes.yaml-mirror.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.yaml-mirror.predicates", List.of("Path=/yaml-mirror/**"));
        p.put("micronaut.gateway.routes.yaml-mirror.filters", List.of("StripPrefix=1", "Mirror=${test.scripted}, 100"));
        return p;
    }

    @BeforeEach
    void shadow() {
        shadowed.clear();
        for (String path : List.of("/inspect/x", "/up/upload", "/up/slow")) {
            scripted.on(path, (request, in, out, socket) -> {
                String body = new String(request.readBody(in), StandardCharsets.UTF_8);
                shadowed.add(new Shadowed(request.target(), request.header("host"), body));
                ScriptedUpstream.write(out, "HTTP/1.1 500 Shadow\r\nContent-Length: 6\r\n\r\nshadow");
                return true;
            });
        }
    }

    private Shadowed next() throws InterruptedException {
        return shadowed.poll(10, TimeUnit.SECONDS);
    }

    @Test
    void mi01TheShadowGetsTheRequestAndItsResponseIsDiscarded() throws Exception {
        RawHttp.Response response = get("/mirrored/inspect/x?q=1", "X-Custom: c");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/x", response.inspected("path"));
        Shadowed shadow = next();
        assertNotNull(shadow);
        assertEquals("/inspect/x?q=1", shadow.target());
        assertTrue(shadow.host().startsWith("127.0.0.1:"), shadow.host());
        // from the configuration
        assertEquals(200, get("/yaml-mirror/inspect/x").status());
        assertEquals("/inspect/x", next().target());
    }

    @Test
    void mi02APercentageOfTheRequestsIsMirroredAndAnInvalidPercentageIsRejected() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, get("/never/inspect/x").status());
        }
        for (int i = 0; i < 40; i++) {
            get("/half/inspect/x");
        }
        Thread.sleep(500);
        int mirrored = shadowed.size();
        assertTrue(mirrored > 5 && mirrored < 35, "mirrored " + mirrored);
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.mirror("http://x", 101));
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.mirror("http://x", -1));
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.mirror("ftp://x", 10));
    }

    @Test
    void mi03TheBodyIsMirroredOrIgnoredAboveTheLimit() throws Exception {
        RawHttp.Response response = send("POST", "/mirrored/up/upload", "payload", "Content-Type: text/plain");
        assertEquals(200, response.status(), response.toString());
        assertEquals("payload", next().body());
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /mirrored/up/upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n4\r\nabcd\r\n");
            Thread.sleep(100);
            http.send("4\r\nefgh\r\n0\r\n\r\n");
            assertEquals(200, http.read(false).status());
        }
        assertEquals("abcdefgh", next().body());
        // over the limit of 16 bytes: the request goes on, nothing is mirrored
        String big = "b".repeat(100);
        assertEquals(200, send("POST", "/mirrored/up/upload", big, "Content-Type: text/plain").status());
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /mirrored/up/upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n14\r\n" + "c".repeat(20) + "\r\n0\r\n\r\n");
            assertEquals(200, http.read(false).status());
        }
        assertNull(shadowed.poll(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void mi04ASlowOrBrokenShadowDoesNotAffectTheRequest() throws Exception {
        scripted.on("/up/slow", (request, in, out, socket) -> {
            Thread.sleep(3000);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            return true;
        });
        long start = System.nanoTime();
        RawHttp.Response response = get("/mirrored/up/slow?ms=0");
        assertEquals(200, response.status());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000, "the request waited for the shadow");
        scripted.on("/inspect/x", (request, in, out, socket) -> false);
        assertEquals(200, get("/mirrored/inspect/x").status(), "a reset of the shadow");
        assertEquals(200, get("/dead-shadow/inspect/x").status(), "a shadow that refuses connections");
    }

    @Test
    void mi05AClientThatGivesUpBeforeTheBodyEndedIsNotMirrored() throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /mirrored/up/upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nabcd\r\n");
            Thread.sleep(200);
        }
        assertNull(shadowed.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void mi06TheShadowHostHasASuffix() throws Exception {
        assertEquals(200, get("/shadow-host/inspect/x").status());
        assertTrue(next().host().startsWith("127.0.0.1-shadow:"));
    }

    @Test
    void mi07TheLimitsOfTheRequestAndOfTheShadowAreIndependent() throws Exception {
        // the route answers 413 above 32 bytes, the shadow has 16: a 20 byte body goes to the upstream only
        assertEquals(200, send("POST", "/limited/up/upload", "d".repeat(20), "Content-Type: text/plain").status());
        assertNull(shadowed.poll(500, TimeUnit.MILLISECONDS));
        assertEquals(413, send("POST", "/limited/up/upload", "d".repeat(40), "Content-Type: text/plain").status());
        assertEquals(200, send("POST", "/limited/up/upload", "d".repeat(10), "Content-Type: text/plain").status());
        assertEquals("dddddddddd", next().body());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            GatewayFilter mirror = GatewayFilters.mirror(scripted, 100, 16, Duration.ofSeconds(1), false);
            gateway.route("mirrored").path("/mirrored/**").filters(GatewayFilters.stripPrefix(1), mirror).uri(upstream);
            gateway.route("never").path("/never/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.mirror(scripted, 0)).uri(upstream);
            gateway.route("half").path("/half/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.mirror(scripted, 50)).uri(upstream);
            gateway.route("dead-shadow").path("/dead-shadow/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.mirror("http://127.0.0.1:" + io.micronaut.core.io.socket.SocketUtils.findAvailableTcpPort(), 100))
                .uri(upstream);
            gateway.route("shadow-host").path("/shadow-host/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.mirror(scripted, 100, 1024, Duration.ofSeconds(1), true)).uri(upstream);
            gateway.route("limited").path("/limited/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(32), mirror).uri(upstream);
        }
    }
}
