package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient http;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "WebSocketRoutesTest"));
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        http.close();
        server.close();
    }

    @Test
    void theHandlersAnswerTheConnection() throws Exception {
        Messages messages = new Messages();
        WebSocket ws = connect("/echo/World", messages, null);
        assertEquals("Hello World", messages.next());
        ws.sendText("hi", true).get(5, TimeUnit.SECONDS);
        assertEquals("echo hi", messages.next());
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void theFiltersOfTheRouteApplyToTheUpgrade() throws Exception {
        ExecutionException rejected = assertThrows(ExecutionException.class, () -> connect("/private", new Messages(), null));
        assertEquals(403, assertInstanceOf(WebSocketHandshakeException.class, rejected.getCause()).getResponse().statusCode());

        Messages messages = new Messages();
        WebSocket ws = connect("/private", messages, "secret");
        assertEquals("welcome", messages.next());
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void theMessagesAreStreams() throws Exception {
        Messages ticks = new Messages();
        WebSocket ticking = connect("/ticks", ticks, null);
        assertEquals("tick 1", ticks.next());
        assertEquals("tick 2", ticks.next());
        assertEquals("tick 3", ticks.next());
        ticking.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);

        Messages upper = new Messages();
        WebSocket ws = connect("/upper", upper, null);
        ws.sendText("hello", true).get(5, TimeUnit.SECONDS);
        ws.sendText("world", true).get(5, TimeUnit.SECONDS);
        assertEquals("HELLO", upper.next());
        assertEquals("WORLD", upper.next());
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void theJobsAreHandledConcurrently() throws Exception {
        Messages messages = new Messages();
        WebSocket ws = connect("/jobs", messages, null);
        ws.sendText("1", true).get(5, TimeUnit.SECONDS);
        ws.sendText("2", true).get(5, TimeUnit.SECONDS);
        // up to 4 at the same time: in any order
        assertEquals(Set.of("done 1", "done 2"), Set.of(messages.next(), messages.next()));
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    private static WebSocket connect(String path, Messages messages, String token) throws Exception {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        if (token != null) {
            builder.header("X-Token", token);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + server.getPort() + path), messages).get(5, TimeUnit.SECONDS);
    }

    /**
     * The text messages of a connection.
     */
    private static final class Messages implements WebSocket.Listener {
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                received.add(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        String next() throws InterruptedException {
            String message = received.poll(5, TimeUnit.SECONDS);
            if (message == null) {
                throw new AssertionError("no message");
            }
            return message;
        }
    }
}
