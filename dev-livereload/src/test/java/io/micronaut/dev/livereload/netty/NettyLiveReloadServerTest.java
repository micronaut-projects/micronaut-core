package io.micronaut.dev.livereload.netty;

import io.micronaut.dev.livereload.LiveReloadScript;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.livereload.LiveReloadServerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyLiveReloadServerTest {

    @Test
    @Timeout(60)
    void theServerIsFoundAsAServiceGreetsAClientServesTheScriptAndSendsReloadCommands() throws Exception {
        LiveReloadServerFactory factory = ServiceLoader.load(LiveReloadServerFactory.class).findFirst().orElseThrow();
        try (LiveReloadServer server = factory.start(0)) {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

            // the script is served for pages without an extension
            HttpResponse<String> script = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/livereload.js?snipver=1")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("content-type").orElse("").startsWith("application/javascript"));
            assertTrue(script.body().contains("livereload"));
            assertTrue(LiveReloadScript.tag(server.port()).contains(":" + server.port() + "/livereload.js"));
            // the path is matched exactly
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/livereload.jsx")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/livereload.js/other")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/other")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

            // a browser connects, says hello, and is told the protocol
            LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
            WebSocket socket = connect(client, server.port(), received);
            socket.sendText("{\"command\":\"hello\",\"protocols\":[\"http://livereload.com/protocols/official-7\"]}", true).get(10, TimeUnit.SECONDS);
            String hello = received.poll(10, TimeUnit.SECONDS);
            assertNotNull(hello);
            assertTrue(hello.contains("\"command\":\"hello\""));
            assertTrue(hello.contains("official-7"));
            assertEquals(1, server.connections());

            // a client that connected but never said hello is not sent the commands, as the protocol has it
            LinkedBlockingQueue<String> silent = new LinkedBlockingQueue<>();
            WebSocket ungreeted = connect(client, server.port(), silent);
            long connected = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (server.connections() < 2 && System.nanoTime() < connected) {
                Thread.sleep(20);
            }
            assertEquals(2, server.connections());

            // the server asks it to reload, and to swap a stylesheet
            server.reload("/", false);
            assertEquals("{\"command\":\"reload\",\"path\":\"/\",\"liveCSS\":false}", received.poll(10, TimeUnit.SECONDS));
            server.reload("/css/app.css", true);
            assertEquals("{\"command\":\"reload\",\"path\":\"/css/app.css\",\"liveCSS\":true}", received.poll(10, TimeUnit.SECONDS));
            // a path with control characters stays valid JSON
            server.reload("/a\"b\\c\nd\te\u0001.css", true);
            assertEquals("{\"command\":\"reload\",\"path\":\"/a\\\"b\\\\c\\nd\\te\\u0001.css\",\"liveCSS\":true}", received.poll(10, TimeUnit.SECONDS));
            assertNull(silent.poll(500, TimeUnit.MILLISECONDS));
            ungreeted.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(10, TimeUnit.SECONDS);

            // a close is honoured
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (server.connections() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(0, server.connections());
        }
    }

    @Test
    @Timeout(60)
    void theLiveReloadSocketAcceptsItsClientsAndRefusesOtherSitesAndOtherPaths() throws Exception {
        try (LiveReloadServer server = NettyLiveReloadServer.start(0)) {
            int port = server.port();
            // a client that is no page, the browser extensions, and the application's pages on a localhost name or address
            assertStatus(101, upgrade(port, "/livereload", null));
            assertStatus(101, upgrade(port, "/livereload", "chrome-extension://jnihajbhpnppcggbcgedagnkighmdlei"));
            assertStatus(101, upgrade(port, "/livereload", "moz-extension://0f8b7a6c-1d2e-4f5a-9b8c-7d6e5f4a3b2c"));
            assertStatus(101, upgrade(port, "/livereload", "http://localhost:8080"));
            assertStatus(101, upgrade(port, "/livereload", "http://127.0.0.1:8080"));
            assertStatus(101, upgrade(port, "/livereload", "http://app.localhost:8080"));
            assertStatus(101, upgrade(port, "/livereload", "http://[::1]:8080"));
            assertStatus(101, upgrade(port, "/livereload?snipver=1", null));

            // a page of another site, a rebinding page, and an opaque origin are refused
            assertStatus(403, upgrade(port, "/livereload", "https://evil.example"));
            assertStatus(403, upgrade(port, "/livereload", "http://rebound.example:" + port));
            assertStatus(403, upgrade(port, "/livereload", "null"));

            // an upgrade to another path is refused rather than left open
            assertStatus(404, upgrade(port, "/other", null));
            assertStatus(404, upgrade(port, "/livereloadx", null));
        }
    }

    @Test
    void aPathIsEscapedAsTheContentsOfAJsonString() {
        assertEquals("/css/app.css", NettyLiveReloadServer.escape("/css/app.css"));
        assertEquals("\\\"\\\\", NettyLiveReloadServer.escape("\"\\"));
        assertEquals("\\b\\f\\n\\r\\t", NettyLiveReloadServer.escape("\b\f\n\r\t"));
        assertEquals("\\u0000\\u001f\\u000b", NettyLiveReloadServer.escape("\u0000\u001f\u000b"));
        // printable and non-ASCII characters are left as they are
        assertEquals("/é/\u007f/ü", NettyLiveReloadServer.escape("/é/\u007f/ü"));
    }

    @Test
    void theSocketIsOpenToClientsThatAreNoPageTheExtensionsAndLoopbackPagesOnly() {
        assertTrue(NettyLiveReloadServer.isAllowedOrigin(null));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("chrome-extension://jnihajbhpnppcggbcgedagnkighmdlei"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("moz-extension://0f8b7a6c-1d2e-4f5a-9b8c-7d6e5f4a3b2c"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("safari-web-extension://abc"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("http://localhost:8080"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("https://LOCALHOST"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("http://app.localhost:8080"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("http://127.0.0.1:8080"));
        assertTrue(NettyLiveReloadServer.isAllowedOrigin("http://[::1]:8080"));

        assertFalse(NettyLiveReloadServer.isAllowedOrigin("https://evil.example"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("http://evillocalhost:8080"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("http://localhost.evil.example"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("http://127.0.0.1.evil.example"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("file://localhost"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("null"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("not an origin"));
        // an application served on a name other than a loopback one, in a dev container or on the LAN, is refused by
        // default: a page there would be indistinguishable from a rebinding page of that name
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("http://devbox.lan:8080"));
        assertFalse(NettyLiveReloadServer.isAllowedOrigin("http://192.168.1.20:8080"));
    }

    private static WebSocket connect(HttpClient client, int port, LinkedBlockingQueue<String> received) throws Exception {
        return client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + port + "/livereload"), new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    received.add(partial.toString());
                    partial.setLength(0);
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }
        }).get(10, TimeUnit.SECONDS);
    }

    private static void assertStatus(int expected, String statusLine) {
        assertTrue(statusLine.contains(" " + expected + " "), statusLine);
    }

    private static String upgrade(int port, String path, @org.jspecify.annotations.Nullable String origin) throws Exception {
        String request = "GET " + path + " HTTP/1.1\r\nHost: localhost:" + port
            + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n"
            + (origin == null ? "" : "Origin: " + origin + "\r\n") + "\r\n";
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
            return status == null ? "" : status;
        } catch (java.net.SocketTimeoutException e) {
            return "no answer";
        }
    }
}
