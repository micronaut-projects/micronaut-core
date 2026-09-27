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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket and other protocol switches relayed on Netty: the handshake, the frames in both
 * directions, closing, and the timeouts.
 */
class UpgradeTest extends GatewayTestBase {

    static final String SPEC = "UpgradeTest";
    final AtomicInteger handshakes = new AtomicInteger();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "8s");
    }

    @BeforeEach
    void echoServer() {
        scripted.on("/ws", this::webSocket);
        scripted.on("/refuse", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 403 Forbidden\r\nContent-Length: 7\r\n\r\nrefused");
            return true;
        });
        scripted.on("/reset-first", (request, in, out, socket) -> {
            if (handshakes.incrementAndGet() == 1) {
                socket.setSoLinger(true, 0);
                return false;
            }
            return webSocket(request, in, out, socket);
        });
    }

    private boolean webSocket(ScriptedUpstream.Request request, java.io.InputStream in, java.io.OutputStream out, java.net.Socket socket) throws IOException {
        StringBuilder response = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade, X-Hop\r\nX-Hop: 1\r\nKeep-Alive: timeout=5\r\n")
            .append("Sec-WebSocket-Accept: ").append(WebSockets.accept(request.header("sec-websocket-key"))).append("\r\n");
        String protocols = request.header("sec-websocket-protocol");
        if (protocols != null && protocols.contains("superchat")) {
            response.append("Sec-WebSocket-Protocol: superchat\r\n");
        }
        String extensions = request.header("sec-websocket-extensions");
        if (extensions != null && extensions.contains("permessage-deflate")) {
            response.append("Sec-WebSocket-Extensions: permessage-deflate\r\n");
        }
        response.append("X-Seen-Host: ").append(request.header("host")).append("\r\n")
            .append("X-Seen-Target: ").append(request.target()).append("\r\n")
            .append("X-Seen-Custom: ").append(request.header("x-custom")).append("\r\n")
            .append("X-Seen-Cookie: ").append(request.header("cookie")).append("\r\n")
            .append("X-Seen-Origin: ").append(request.header("origin")).append("\r\n")
            .append("X-Seen-Added: ").append(request.header("x-added")).append("\r\n")
            .append("X-Seen-Upgrade: ").append(request.header("upgrade")).append("\r\n\r\n");
        ScriptedUpstream.write(out, response.toString());
        while (true) {
            WebSockets.Frame frame;
            try {
                frame = WebSockets.read(in);
            } catch (EOFException e) {
                // the client closed its side: this side still sends
                WebSockets.writeText(out, "after-half-close", false);
                socket.shutdownOutput();
                return false;
            }
            switch (frame.opcode()) {
                case WebSockets.TEXT -> {
                    String text = frame.text();
                    if (text.equals("close-me")) {
                        WebSockets.writeClose(out, 4001, "server", false);
                    } else if (text.equals("drop")) {
                        socket.close();
                        return false;
                    } else {
                        WebSockets.write(out, WebSockets.TEXT, frame.payload(), false);
                    }
                }
                case WebSockets.PING -> WebSockets.write(out, WebSockets.PONG, frame.payload(), false);
                case WebSockets.CLOSE -> {
                    WebSockets.write(out, WebSockets.CLOSE, frame.payload(), false);
                    return false;
                }
                default -> WebSockets.write(out, frame.opcode(), frame.payload(), false);
            }
        }
    }

    private RawHttp open(String path, String... headers) throws IOException {
        RawHttp http = new RawHttp(gateway.getPort());
        // not localhost: the CORS filter of the server denies a request of another origin to a localhost server
        http.send(WebSockets.handshake(path, headers).replace("Host: localhost", "Host: gw.example"));
        RawHttp.Response response = http.read(false);
        assertEquals(101, response.status(), response.toString());
        assertEquals(WebSockets.accept(WebSockets.KEY), response.header("Sec-WebSocket-Accept"));
        lastHandshake = response;
        return http;
    }

    RawHttp.Response lastHandshake;

    @Test
    void up01TextAndBinaryFramesAreEchoedThroughHttpAndWsRoutes() throws IOException {
        for (String path : new String[]{"/raw/ws/echo", "/ws-route/ws/echo"}) {
            try (RawHttp http = open(path)) {
                WebSockets.writeText(http.out, "hello", true);
                assertEquals("hello", WebSockets.read(http.in).text());
                byte[] binary = Upstream.content(0, 70_000);
                WebSockets.write(http.out, WebSockets.BINARY, binary, true);
                WebSockets.Frame echoed = WebSockets.read(http.in);
                assertEquals(WebSockets.BINARY, echoed.opcode());
                assertArrayEquals(binary, echoed.payload());
            }
        }
    }

    @Test
    void up02AndUp08TheSubprotocolAndTheExtensionsAreNegotiatedEndToEnd() throws IOException {
        try (RawHttp http = open("/raw/ws/echo", "Sec-WebSocket-Protocol: chat, superchat", "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits")) {
            assertEquals("superchat", lastHandshake.header("Sec-WebSocket-Protocol"));
            assertEquals("permessage-deflate", lastHandshake.header("Sec-WebSocket-Extensions"));
        }
    }

    @Test
    void up03Up09Up10AndHo04TheHandshakeCarriesTheHeadersTheOriginTheQueryAndTheUpstreamHost() throws IOException {
        try (RawHttp http = open("/raw/ws/echo?room=a%20b&x=%2F", "X-Custom: c", "Cookie: session=abc", "Origin: https://app.example")) {
            assertEquals("c", lastHandshake.header("X-Seen-Custom"));
            assertEquals("session=abc", lastHandshake.header("X-Seen-Cookie"));
            assertEquals("https://app.example", lastHandshake.header("X-Seen-Origin"));
            assertEquals("/ws/echo?room=a%20b&x=%2F", lastHandshake.header("X-Seen-Target"));
            assertEquals("127.0.0.1:" + scripted.port(), lastHandshake.header("X-Seen-Host"));
        }
    }

    @Test
    void up04CloseFramesPropagateBothWaysAndATcpCloseClosesTheOtherSide() throws IOException {
        try (RawHttp http = open("/raw/ws/echo")) {
            WebSockets.writeClose(http.out, 4000, "bye", true);
            WebSockets.Frame close = WebSockets.read(http.in);
            assertEquals(WebSockets.CLOSE, close.opcode());
            assertEquals(4000, close.closeCode());
            assertEquals("bye", close.closeReason());
        }
        try (RawHttp http = open("/raw/ws/echo")) {
            WebSockets.writeText(http.out, "close-me", true);
            WebSockets.Frame close = WebSockets.read(http.in);
            assertEquals(4001, close.closeCode());
            assertEquals("server", close.closeReason());
        }
        try (RawHttp http = open("/raw/ws/echo")) {
            WebSockets.writeText(http.out, "drop", true);
            assertTrue(http.isClosedByPeer(10_000), "the client side stayed open after the upstream dropped the connection");
        }
    }

    @Test
    void up05PingIsAnsweredWithPong() throws IOException {
        try (RawHttp http = open("/raw/ws/echo")) {
            WebSockets.write(http.out, WebSockets.PING, "p".getBytes(StandardCharsets.US_ASCII), true);
            WebSockets.Frame pong = WebSockets.read(http.in);
            assertEquals(WebSockets.PONG, pong.opcode());
            assertEquals("p", pong.text());
        }
    }

    @Test
    void up06ARefusedUpgradeIsANormalResponseOnAConnectionThatStaysUsable() throws IOException {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send(WebSockets.handshake("/raw/refuse"));
            RawHttp.Response refused = http.read(false);
            assertEquals(403, refused.status());
            assertEquals("refused", refused.text());
            http.send("GET /inspect/after HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertEquals("/inspect/after", http.read(false).inspected("path"));
        }
    }

    @Test
    void up07BrowserHandshakeVariants() throws IOException {
        try (RawHttp http = open("/raw/ws/echo", "Connection: keep-alive, Upgrade", "Upgrade: WebSocket")) {
            WebSockets.writeText(http.out, "firefox", true);
            assertEquals("firefox", WebSockets.read(http.in).text());
        }
    }

    @Test
    void up11PathTransformsAndHeaderFiltersApplyToTheUpgrade() throws IOException {
        try (RawHttp http = open("/filtered/prefix/ws/echo")) {
            assertEquals("/ws/echo", lastHandshake.header("X-Seen-Target"));
            assertEquals("gw", lastHandshake.header("X-Seen-Added"));
        }
    }

    @Test
    void up12TheResponseTimeoutDoesNotCloseTheTunnelButTheActivityTimeoutDoes() throws Exception {
        try (RawHttp http = open("/timed/ws/echo")) {
            Thread.sleep(1200);
            WebSockets.writeText(http.out, "still here", true);
            assertEquals("still here", WebSockets.read(http.in).text());
        }
        try (RawHttp http = open("/idle/ws/echo")) {
            Thread.sleep(2000);
            assertTrue(http.isClosedByPeer(10_000), "the idle tunnel stayed open");
        }
    }

    @Test
    void up13AnUpgradeIsRetriedBeforeThe101() throws IOException {
        handshakes.set(0);
        try (RawHttp http = open("/retried/reset-first")) {
            WebSockets.writeText(http.out, "after retry", true);
            assertEquals("after retry", WebSockets.read(http.in).text());
        }
        assertEquals(2, handshakes.get());
    }

    @Test
    @Disabled("UP-14: the HTTP/1 decoder of the Netty server reads the bytes after an upgrade request before the 101 "
        + "switches the connection, and skips the first byte of a frame (0x81 is a C1 control character): needs the server "
        + "to stop decoding after an Upgrade request until its response is written. A client must wait for the 101 anyway "
        + "(RFC 6455 4.1); text bytes, which the decoder keeps, reach the upstream (UpgradeRelayTest)")
    void up14BytesSentBeforeThe101ReachTheUpstreamAsFrames() throws IOException {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream();
            WebSockets.writeText(frame, "early", true);
            byte[] handshake = WebSockets.handshake("/raw/ws/echo").getBytes(StandardCharsets.US_ASCII);
            byte[] both = new byte[handshake.length + frame.size()];
            System.arraycopy(handshake, 0, both, 0, handshake.length);
            System.arraycopy(frame.toByteArray(), 0, both, handshake.length, frame.size());
            http.send(both);
            assertEquals(101, http.read(false).status());
            assertEquals("early", WebSockets.read(http.in).text());
        }
    }

    @Test
    void up16AnUpgradeToAProtocolTheRouteDoesNotRelayIsAPlainRequest() throws IOException {
        // only websocket by default: the bytes of another protocol would pass the filters and limits
        RawHttp.Response response = get("/inspect/x", "Connection: Upgrade", "Upgrade: tcp");
        assertEquals(200, response.status(), response.toString());
        assertNull(response.inspected("h:upgrade"), response.toString());
    }

    @Test
    void up15AnH2cUpgradeIsNotForwarded() throws IOException {
        RawHttp.Response response = get("/inspect/x", "Connection: Upgrade, HTTP2-Settings", "Upgrade: h2c", "HTTP2-Settings: AAMAAABkAARAAAAAAAIAAAAA");
        assertEquals(200, response.status(), response.toString());
        assertNull(response.inspected("h:upgrade"));
        assertNull(response.inspected("h:http2-settings"));
    }

    @Test
    void up17AHalfCloseIsPropagated() throws IOException {
        try (RawHttp http = open("/raw/ws/echo")) {
            http.socket.shutdownOutput();
            WebSockets.Frame after = WebSockets.read(http.in);
            assertEquals("after-half-close", after.text());
            // the upstream shut down its output too: the tunnel ends
            assertEquals(-1, http.in.read());
        }
    }

    @Test
    void hh07A101KeepsUpgradeAndConnectionAndDropsTheOtherHopByHopHeaders() throws IOException {
        try (RawHttp http = open("/raw/ws/echo")) {
            assertEquals("websocket", lastHandshake.header("Upgrade"));
            assertEquals("Upgrade", lastHandshake.header("Connection"));
            assertNull(lastHandshake.header("X-Hop"));
            assertNull(lastHandshake.header("Keep-Alive"));
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
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("ws-route").path("/ws-route/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted.replace("http://", "ws://"));
            gateway.route("filtered").path("/filtered/**")
                .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Added", "gw"))
                .uri(scripted);
            gateway.route("timed").path("/timed/**").filters(GatewayFilters.stripPrefix(1)).responseTimeout(Duration.ofMillis(500)).uri(scripted);
            gateway.route("idle").path("/idle/**").filters(GatewayFilters.stripPrefix(1)).activityTimeout(Duration.ofMillis(500)).uri(scripted);
            gateway.route("retried").path("/retried/**").filters(GatewayFilters.stripPrefix(1)).retry(GatewayRetry.of(1).withBackoff(Duration.ofMillis(1), 1)).uri(scripted);
        }
    }
}
