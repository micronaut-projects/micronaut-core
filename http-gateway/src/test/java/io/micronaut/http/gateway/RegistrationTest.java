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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How gateway routes become the routes of the application: exact templates and prefixes, the
 * precedence among the other routes, the error responses of the upstream, upgrades, and a plain
 * route of the application with a {@link ProxyHandler}.
 */
class RegistrationTest extends GatewayTestBase {

    static final String SPEC = "RegistrationTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void anExactTemplateKeepsItsPathVariable() throws IOException {
        RawHttp.Response response = get("/exact/a%20b/tail");
        assertEquals(200, response.status(), response.toString());
        assertEquals("a b", response.header("X-Path-Variable"), "the decoded value of the variable of the template");
        assertEquals("/inspect/exact/a%20b/tail", response.inspected("path"), "the raw path is relayed");
        assertEquals(404, get("/exact/a/tail/more").status(), "an exact template routes its own paths only");
    }

    @Test
    void aPrefixWithAPathVariableFailsTheStartup() {
        assertThrows(RuntimeException.class, () -> startGateway("RegistrationTest.invalid", upstream, Map.of()).getApplicationContext().close());
    }

    @Test
    void theRoutesOfTheApplicationAndTheGatewayRoutesAreRoutedTogether() throws IOException {
        assertEquals("local special", get("/mixed/special").text(), "a more specific route of the application");
        assertEquals("/inspect/mixed/other", get("/mixed/other").inspected("path"), "the gateway route of the prefix");
        assertEquals("/inspect/tie/x", get("/tie/x").inspected("path"), "the order breaks a tie, the gateway route first");
        assertEquals("local method", get("/method/x").text(), "a route of the method of the request before a gateway route, whatever the order");
        assertEquals("/inspect/method/x", send("DELETE", "/method/x", null).inspected("path"), "the gateway route for the other methods");
    }

    @Test
    void theErrorResponsesOfTheUpstreamPassThroughTheStatusRoutes() throws IOException {
        scripted.on("/missing", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nX-Upstream: yes\r\nContent-Length: 16\r\n\r\nupstream missing");
            return true;
        });
        scripted.on("/broken", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\nX-Upstream: yes\r\nContent-Length: 15\r\n\r\nupstream broken");
            return true;
        });
        RawHttp.Response missing = get("/scripted/missing");
        assertEquals(404, missing.status(), missing.toString());
        assertEquals("upstream missing", missing.text());
        assertEquals("yes", missing.header("X-Upstream"));
        RawHttp.Response broken = get("/scripted/broken");
        assertEquals(500, broken.status(), broken.toString());
        assertEquals("upstream broken", broken.text());
        // the status routes still answer the routes of the application
        assertEquals("global not found", get("/no-route-here/at-all-x").text());
        assertEquals("group not found", get("/statuses/local").text());
    }

    @Test
    void aWebSocketOfTheApplicationTakesTheUpgradeBeforeAGatewayRoute() throws IOException {
        String response = upgrade("/local-ws");
        assertTrue(response.startsWith("HTTP/1.1 101"), response);
    }

    @Test
    void anUpstreamThatDeclinesTheUpgradeIsRelayed() throws IOException {
        scripted.on("/no-upgrade", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 10\r\n\r\nno upgrade");
            return true;
        });
        String response = upgrade("/scripted/no-upgrade");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith("no upgrade"), response);
    }

    @Test
    void aPlainRouteOfTheApplicationRelaysWithAProxyHandler() throws IOException {
        RawHttp.Response response = get("/plain/orders/1?q=a%20b");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/orders/1", response.inspected("path"));
        assertEquals("q=a%20b", response.inspected("query"));
        assertEquals(null, response.header("X-Path-Variable"), "the route filters of the gateway route do not apply");
    }

    private String upgrade(String path) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", gateway.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            InputStream in = socket.getInputStream();
            StringBuilder text = new StringBuilder();
            int contentLength = -1;
            String line;
            while (!(line = line(in)).isEmpty()) {
                text.append(line).append("\n");
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            if (contentLength > 0) {
                text.append(new String(in.readNBytes(contentLength), StandardCharsets.UTF_8));
            }
            return text.toString();
        }
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') {
                line.append((char) c);
            }
        }
        return line.toString();
    }

    /**
     * Exposes the path variable named {@code path} of the gateway route.
     */
    static final class PathVariableHeader implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            exchange.pathVariables().findString(GatewayRouteSpec.PATH_VARIABLE)
                .ifPresent(value -> exchange.request().setAttribute("test.path-variable", value));
        }

        @Override
        public void configure(io.micronaut.web.router.builder.RouteFilterSpec<?> route) {
            route.after((request, response) -> request.getAttribute("test.path-variable", String.class)
                .ifPresent(value -> response.header("X-Path-Variable", value)));
        }
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
            gateway.route("exact").path("/exact/{path}/tail").filters(GatewayFilters.prefixPath("/inspect"), new PathVariableHeader()).uri(upstream);
            gateway.route("mixed").path("/mixed/**").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("tie").path("/tie/{name}").order(-1).filters(GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("method").path("/method/{name}").order(-1).filters(GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("scripted").path("/scripted/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("local-ws").path("/local-ws/**").uri(scripted);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class ApplicationRoutes implements HttpRoutes {
        private final ProxyHandlerFactory proxies;
        private final String upstream;

        ApplicationRoutes(ProxyHandlerFactory proxies, @Value("${test.upstream}") String upstream) {
            this.proxies = proxies;
            this.upstream = upstream;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("global not found").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.status(HttpStatus.INTERNAL_SERVER_ERROR, request -> HttpResponse.serverError("global server error").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/mixed/special", (request, pathVariables) -> HttpResponse.ok("local special").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.any("/tie/{name}").handle((request, pathVariables) -> HttpResponse.ok("local tie").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/method/{name}", (request, pathVariables) -> HttpResponse.ok("local method").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.path("/statuses", group -> {
                group.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("group not found").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.GET("/local", (request, pathVariables) -> HttpResponse.notFound());
            });
            ProxyHandler orders = proxies.create(GatewayRoute.builder("plain-orders")
                .uri(upstream)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.prefixPath("/inspect"), new PathVariableHeader())
                .build());
            routes.path("/plain", group -> {
                group.any("/orders").consumesAll().keepErrorResponses().handleAsync(orders);
                group.any("/orders/{+path}").consumesAll().keepErrorResponses().handleAsync(orders);
            });
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerWebSocket("/local-ws")
    static class LocalWebSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RegistrationTest.invalid")
    static class InvalidRoutes implements GatewayRoutes {
        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("invalid").path("/invalid/{path}/**").uri("http://localhost:1");
        }
    }
}
