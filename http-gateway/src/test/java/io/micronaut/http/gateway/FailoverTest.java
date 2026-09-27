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
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A failover to a secondary upstream, on a failure of the primary or on a status, with the body
 * kept up to a maximum size (CB-11).
 */
class FailoverTest extends GatewayTestBase {

    static final String SPEC = "FailoverTest";
    static final int DEAD_PORT = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "5s");
        // a failover from the configuration: on a 503 of the primary
        p.put("micronaut.gateway.routes.yaml-failover.uri", "${test.scripted}");
        p.put("micronaut.gateway.routes.yaml-failover.predicates", List.of("Path=/up/**", "Header=X-Yaml"));
        p.put("micronaut.gateway.routes.yaml-failover.order", -10);
        p.put("micronaut.gateway.routes.yaml-failover.filters[0].name", "CircuitBreaker");
        p.put("micronaut.gateway.routes.yaml-failover.filters[0].args.name", "yaml-failover");
        p.put("micronaut.gateway.routes.yaml-failover.filters[0].args.fallback-uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.yaml-failover.filters[0].args.fallback-statuses", "503");
        p.put("micronaut.gateway.routes.yaml-failover.filters[0].args.failure-statuses", "504");
        return p;
    }

    private RawHttp.Response post(String header, String first, String second) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /up/upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n" + header + "\r\n\r\n"
                + Integer.toHexString(first.length()) + "\r\n" + first + "\r\n");
            Thread.sleep(200);
            http.send(Integer.toHexString(second.length()) + "\r\n" + second + "\r\n0\r\n\r\n");
            return http.read(false);
        }
    }

    @Test
    void cb11AFailureOfThePrimaryFailsOverWithTheBody() throws Exception {
        RawHttp.Response response = post("X-Failover: 1", "hello ", "world");
        assertEquals(200, response.status(), response.toString());
        assertEquals("POST " + Upstream.digest("hello world".getBytes(StandardCharsets.UTF_8)), response.text());
    }

    @Test
    void cb11AStatusOfThePrimaryFailsOverWithTheBody() throws Exception {
        scripted.on("/up/upload", (request, in, out, socket) -> {
            request.readBody(in);
            ScriptedUpstream.write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n");
            return true;
        });
        RawHttp.Response response = post("X-Yaml: 1", "from ", "yaml");
        assertEquals(200, response.status(), response.toString());
        assertEquals("POST " + Upstream.digest("from yaml".getBytes(StandardCharsets.UTF_8)), response.text());
    }

    @Test
    void cb11ABodyOverTheLimitIsNotKeptForTheFailover() throws Exception {
        RawHttp.Response response = post("X-Small: 1", "more than ", "eight bytes");
        // the secondary cannot read the body: the failover fails
        assertEquals(true, response.status() >= 500, response.toString());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final ProxyHandlerFactory factory;

        Routes(@Value("${test.upstream}") String upstream, ProxyHandlerFactory factory) {
            this.upstream = upstream;
            this.factory = factory;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            String dead = "http://127.0.0.1:" + DEAD_PORT;
            gateway.route("failover").path("/up/**").where(RequestCondition.custom(request -> request.getHeaders().contains("X-Failover"))).order(-10)
                .circuitBreaker(GatewayCircuitBreaker.of("failover", (request, pv) -> factory.to(upstream).handle(request, pv))
                    .withMaxFallbackBodyBytes(1024))
                .uri(dead);
            gateway.route("failover-small").path("/up/**").where(RequestCondition.custom(request -> request.getHeaders().contains("X-Small"))).order(-10)
                .circuitBreaker(GatewayCircuitBreaker.of("failover-small", (request, pv) -> factory.to(upstream).handle(request, pv))
                    .withMaxFallbackBodyBytes(8))
                .uri(dead);
        }
    }
}
