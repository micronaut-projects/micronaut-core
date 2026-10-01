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
}
