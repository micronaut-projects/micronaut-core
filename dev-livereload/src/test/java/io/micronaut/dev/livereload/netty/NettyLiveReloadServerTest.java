package io.micronaut.dev.livereload.netty;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            assertTrue(LiveReloadServer.scriptTag(server.port()).contains(":" + server.port() + "/livereload.js"));
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/other")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

            // a browser connects, says hello, and is told the protocol
            LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
            WebSocket socket = client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/livereload"), new WebSocket.Listener() {
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
            socket.sendText("{\"command\":\"hello\",\"protocols\":[\"http://livereload.com/protocols/official-7\"]}", true).get(10, TimeUnit.SECONDS);
            String hello = received.poll(10, TimeUnit.SECONDS);
            assertNotNull(hello);
            assertTrue(hello.contains("\"command\":\"hello\""));
            assertTrue(hello.contains("official-7"));
            assertEquals(1, server.connections());

            // the server asks it to reload, and to swap a stylesheet
            server.reload("/", false);
            assertEquals("{\"command\":\"reload\",\"path\":\"/\",\"liveCSS\":false}", received.poll(10, TimeUnit.SECONDS));
            server.reload("/css/app.css", true);
            assertEquals("{\"command\":\"reload\",\"path\":\"/css/app.css\",\"liveCSS\":true}", received.poll(10, TimeUnit.SECONDS));

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

    @Test
    @Timeout(60)
    void aMountedDirectoryIsServedWithTheScriptInItsPagesAndNothingOutsideIt(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path reports = java.nio.file.Files.createDirectories(directory.resolve("reports"));
        java.nio.file.Files.writeString(reports.resolve("index.html"), "<html><body><h1>Tests</h1></body></html>");
        java.nio.file.Files.writeString(reports.resolve("events.ndjson"), "{}\n");
        java.nio.file.Files.writeString(directory.resolve("secret.txt"), "secret");
        try (LiveReloadServer server = NettyLiveReloadServer.start(0)) {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            String address = server.serve("/reports/tests", reports);
            assertEquals("http://localhost:" + server.port() + "/reports/tests/", address);
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> index = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, index.statusCode());
            assertTrue(index.headers().firstValue("content-type").orElse("").startsWith("text/html"));
            assertEquals("<html><body><h1>Tests</h1>" + LiveReloadServer.scriptTag(server.port()) + "</body></html>", index.body());
            HttpResponse<String> events = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/events.ndjson")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("{}\n", events.body());
            assertEquals("application/x-ndjson", events.headers().firstValue("content-type").orElse(""));

            // a larger file is streamed as it is, and a HEAD request gets its headers only
            byte[] large = new byte[3 * 1024 * 1024];
            new java.util.Random(7).nextBytes(large);
            java.nio.file.Files.write(reports.resolve("data.bin"), large);
            HttpResponse<byte[]> streamed = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/data.bin")).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, streamed.statusCode());
            org.junit.jupiter.api.Assertions.assertArrayEquals(large, streamed.body());
            HttpResponse<byte[]> head = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/data.bin")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals(String.valueOf(large.length), head.headers().firstValue("content-length").orElse(""));
            assertEquals(0, head.body().length);

            // without the slash, relative links would resolve against the parent: redirected
            HttpResponse<String> bare = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(301, bare.statusCode());
            assertEquals("/reports/tests/", bare.headers().firstValue("location").orElse(""));

            // nothing outside the directory, encoded or not
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/../secret.txt")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/%2E%2E/secret.txt")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/missing.html")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

            // a mounted page is the server's own: no other origin may read it, and no other host name
            assertTrue(index.headers().firstValue("access-control-allow-origin").isEmpty());
            assertTrue(rawStatusLine(server.port(), "GET /reports/tests/ HTTP/1.1\r\nHost: rebound.example:" + server.port() + "\r\n\r\n").contains("403"));
            assertTrue(rawStatusLine(server.port(), "GET /reports/tests/ HTTP/1.1\r\nHost: localhost:" + server.port() + "\r\n\r\n").contains("200"));

            // a directory deeper in the mount: redirected to its slash, then its index
            java.nio.file.Path suite = java.nio.file.Files.createDirectories(reports.resolve("suite"));
            java.nio.file.Files.writeString(suite.resolve("index.html"), "<p>suite</p>");
            HttpResponse<String> nested = client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/suite")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(301, nested.statusCode());
            assertEquals("/reports/tests/suite/", nested.headers().firstValue("location").orElse(""));
            assertTrue(client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/suite/")).build(), HttpResponse.BodyHandlers.ofString()).body().startsWith("<p>suite</p>"));

            // a link out of the directory leads nowhere
            java.nio.file.Files.createSymbolicLink(reports.resolve("escape.txt"), directory.resolve("secret.txt"));
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/escape.txt")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

            // the longest prefix wins, whatever the order of the mounts
            java.nio.file.Path all = java.nio.file.Files.createDirectories(directory.resolve("all"));
            java.nio.file.Files.writeString(all.resolve("index.html"), "<p>all</p>");
            server.serve("/reports/", all);
            assertTrue(client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/")).build(), HttpResponse.BodyHandlers.ofString()).body().contains("<h1>Tests</h1>"));
            assertTrue(client.send(HttpRequest.newBuilder(URI.create(base + "/reports/")).build(), HttpResponse.BodyHandlers.ofString()).body().contains("<p>all</p>"));
            server.unserve("/reports/");

            server.unserve("/reports/tests/");
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/reports/tests/")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> server.serve("/", reports));
        }
    }

    @Test
    @Timeout(60)
    void aPageListeningToATopicReceivesWhatIsPublishedOnItAndNothingElse() throws Exception {
        try (LiveReloadServer server = NettyLiveReloadServer.start(0)) {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            LinkedBlockingQueue<String> tests = listen(client, server, "tests");
            LinkedBlockingQueue<String> other = listen(client, server, "other");
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while ((server.subscribers("tests") < 1 || server.subscribers("other") < 1) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, server.subscribers("tests"));
            assertEquals(0, server.connections(), "the event channel is not a LiveReload client");

            server.publish("tests", "{\"type\":\"testFinished\",\"status\":\"PASSED\"}");
            assertEquals("{\"type\":\"testFinished\",\"status\":\"PASSED\"}", tests.poll(10, TimeUnit.SECONDS));
            assertEquals(null, other.poll(200, TimeUnit.MILLISECONDS));
            server.publish("nobody", "{}");

            // a page of another origin may not follow the events: a WebSocket is not bound by the same-origin policy
            String foreign = rawStatusLine(server.port(), "GET " + LiveReloadServer.EVENTS_PATH + "?topic=tests HTTP/1.1\r\nHost: localhost:" + server.port()
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nOrigin: https://evil.example\r\n\r\n");
            assertTrue(foreign.contains("403"), foreign);
            String own = rawStatusLine(server.port(), "GET " + LiveReloadServer.EVENTS_PATH + "?topic=tests HTTP/1.1\r\nHost: localhost:" + server.port()
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nOrigin: http://localhost:" + server.port() + "\r\n\r\n");
            assertTrue(own.contains("101"), own);
            // a topic goes with its last listener; the raw connection above closed
            long gone = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (server.subscribers("tests") > 1 && System.nanoTime() < gone) {
                Thread.sleep(20);
            }
            assertEquals(1, server.subscribers("tests"));
        }
    }

    private static String rawStatusLine(int port, String request) throws Exception {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
        }
    }

    private static LinkedBlockingQueue<String> listen(HttpClient client, LiveReloadServer server, String topic) throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + server.port() + LiveReloadServer.EVENTS_PATH + "?topic=" + topic), new WebSocket.Listener() {
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
        return received;
    }
}
