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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WebSocket upgrade request that no WebSocket route of the server takes is routed like any
 * other request: a route may switch the protocols with the {@code 101} response of an upstream
 * it relays the upgrade to, and any other response declines the upgrade. A WebSocket route of
 * the path takes the upgrade first.
 */
class WebSocketUpgradeToOtherRouteTest {
    private static final String SPEC_NAME = "WebSocketUpgradeToOtherRouteTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aRouteThatRelaysTheUpgradeSwitchesTheProtocols() throws IOException {
        String response = upgrade("/relay/echo");
        assertTrue(response.startsWith("HTTP/1.1 101"), response);
        assertTrue(response.toLowerCase().contains("sec-websocket-accept:"), response);
    }

    @Test
    void aPlainRouteDeclinesTheUpgradeWithItsResponse() throws IOException {
        String response = upgrade("/plain");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith("plain websocket"), response);
    }

    @Test
    void aWebSocketTakesTheUpgradeBeforeAnotherRouteOfThePath() throws IOException {
        String response = upgrade("/echo");
        assertTrue(response.startsWith("HTTP/1.1 101"), response);
    }

    @Test
    void anUpgradeWithoutAnyRouteIsNotFound() throws IOException {
        String response = upgrade("/nothing-here");
        assertTrue(response.startsWith("HTTP/1.1 404"), response);
    }

    private static String upgrade(String path) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
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

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final RawHttpClient client;
        private final BeanProvider<EmbeddedServer> embeddedServer;

        Routes(RawHttpClient client, BeanProvider<EmbeddedServer> embeddedServer) {
            this.client = client;
            this.embeddedServer = embeddedServer;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.any("/relay/{+path}").consumesAll().handleAsync((request, pathVariables) -> {
                // relays the upgrade to the WebSocket of this server, which switches the protocols
                MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(),
                    embeddedServer.get().getURI().resolve("/" + pathVariables.getString("path")).toString());
                request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
                return Mono.<HttpResponse<?>>from(client.exchange(outbound, ((ServerHttpRequest<?>) request).byteBody().move(), null, RawRequestOptions.proxy()))
                    .toFuture();
            });
            routes.GET("/plain", (request, pathVariables) -> HttpResponse.ok("plain " + request.getHeaders().get("Upgrade"))
                .contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/echo", (request, pathVariables) -> HttpResponse.ok("not the WebSocket"));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/echo")
    static class EchoWebSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message);
        }
    }
}
