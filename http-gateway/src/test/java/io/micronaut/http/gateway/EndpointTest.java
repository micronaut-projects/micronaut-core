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

import io.micronaut.core.io.socket.SocketUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The read-only gateway endpoint: sensitive by default, without write operations, and on the
 * management port only when one is configured.
 */
class EndpointTest extends GatewayTestBase {

    private final int managementPort = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return "EndpointTest";
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String r = "micronaut.gateway.routes.";
        p.put(r + "orders.uri", "${test.upstream}");
        p.put(r + "orders.order", 3);
        p.put(r + "orders.predicates", List.of("Path=/api/orders/**", "Header=X-Tenant"));
        p.put(r + "orders.filters", List.of("StripPrefix=2", "AddRequestHeader=X-Gw, mn", "Retry=2", "CircuitBreaker=orders"));
        p.put(r + "orders.metadata.team", "orders");
        p.put(r + "catch-all.uri", "${test.upstream}");
        p.put(r + "catch-all.order", 100);
        p.put("endpoints.all.port", managementPort);
        p.put("endpoints.gateway.enabled", true);
        p.put("endpoints.gateway.sensitive", false);
        p.put("endpoints.routes.enabled", true);
        return p;
    }

    private RawHttp.Response management(String method, String path) throws IOException {
        return RawHttp.exchange(managementPort, method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" + (method.equals("GET") ? "" : "Content-Length: 0\r\n") + "\r\n");
    }

    @Test
    void theEndpointListsTheGatewayRoutes() throws IOException {
        RawHttp.Response response = management("GET", "/gateway");
        assertEquals(200, response.status(), response.toString());
        String json = response.text();
        assertTrue(json.contains("\"id\":\"orders\""), json);
        assertTrue(json.contains("\"order\":3"), json);
        assertTrue(json.contains("/api/orders/**"), json);
        assertTrue(json.contains("StripPrefix=2"), json);
        assertTrue(json.contains("AddRequestHeader=X-Gw, mn"), json);
        assertTrue(json.contains("\"retries\":2"), json);
        assertTrue(json.contains("\"name\":\"orders\""), json);
        assertTrue(json.contains("\"state\":\"CLOSED\""), json);
        assertTrue(json.contains("\"team\":\"orders\""), json);
        assertTrue(json.contains("/api/orders/{+path}"), json);
        RawHttp.Response one = management("GET", "/gateway/orders");
        assertEquals(200, one.status(), one.toString());
        assertTrue(one.text().startsWith("{\"id\":\"orders\""), one.text());
        assertEquals(404, management("GET", "/gateway/missing").status());
    }

    @Test
    void mg03TheEndpointHasNoWriteOperation() throws IOException {
        for (String method : List.of("POST", "PUT", "DELETE")) {
            int status = management(method, "/gateway/orders").status();
            assertTrue(status == 404 || status == 405, method + " " + status);
        }
    }

    @Test
    void mg06AndRt18TheEndpointIsNotReachableThroughTheGatewayPortAndIsSensitiveByDefault() throws IOException {
        // on the application port the endpoint is never served: denied, or relayed to the upstream, which has no such path
        RawHttp.Response relayed = get("/gateway");
        assertTrue(relayed.status() == 401 || relayed.status() == 404, relayed.toString());
        assertTrue(!relayed.text().contains("\"routes\""), relayed.toString());
        // sensitive by default: another gateway without the override
        io.micronaut.runtime.server.EmbeddedServer server = io.micronaut.context.ApplicationContext.run(io.micronaut.runtime.server.EmbeddedServer.class, Map.of(
            "spec.name", "EndpointTest-sensitive",
            "micronaut.server.port", -1,
            "endpoints.gateway.enabled", true,
            "micronaut.gateway.routes.x.uri", "http://localhost:1",
            "micronaut.gateway.routes.x.predicates", List.of("Path=/x/**")));
        try {
            RawHttp.Response sensitive = RawHttp.exchange(server.getPort(), "GET /gateway HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(401, sensitive.status(), sensitive.toString());
        } finally {
            server.getApplicationContext().close();
        }
    }
}
