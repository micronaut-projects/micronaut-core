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
package io.micronaut.http.server.tck.tests.websocket;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_ASYNC_HANDLERS;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_BINARY;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_BROADCAST;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_CLOSE_HANDSHAKE;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_ERROR_HANDLING;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_FRAGMENTATION;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_HANDSHAKE_ERRORS;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_MAX_PAYLOAD;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_PING_PONG;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_REQUEST_SCOPE;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_SUBPROTOCOL;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_UPGRADE_FILTER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ServerWebSocket} beans, driven by the JDK WebSocket client. Each operation is tested
 * like for the routes of {@link FunctionalWebSocketRouteTest}, with the same tags, except for
 * the handlers that only routes have: a message stream handler, a ping handler, and no message
 * handler, as a bean has an {@code @OnMessage} method.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
@Tag(WEBSOCKET)
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class ServerWebSocketTest {
    public static final String SPEC_NAME = "ServerWebSocketTest";

    private static final long TIMEOUT = WebSocketTestClient.TIMEOUT;

    private static ServerUnderTest server;
    private static WebSocketTestClient client;

    @BeforeAll
    static void start() {
        server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of());
        client = new WebSocketTestClient(server);
    }

    @AfterAll
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    static void stop() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void clearEvents() {
        events().clear();
    }

    @Test
    void textMessagesAreEchoedWithThePathVariables() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/echo/lobby");
        assertEquals("open lobby", connection.next());
        connection.send("hello");
        assertEquals("echo lobby hello", connection.next());
        connection.send("again");
        assertEquals("echo lobby again", connection.next());
        connection.close(1000, "bye");
        assertEquals("close lobby 1000 bye", event());
    }

    @Test
    @Tag(WEBSOCKET_FRAGMENTATION)
    void aMessageInFragmentsIsOneMessage() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/echo/fragments");
        assertEquals("open fragments", connection.next());
        connection.ws.sendText("hel", false).get(TIMEOUT, TimeUnit.SECONDS);
        connection.ws.sendText("lo", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo fragments hello", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_BINARY)
    void binaryMessagesAreBytes() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/binary");
        connection.ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true).get(TIMEOUT, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{3, 2, 1}, assertInstanceOf(byte[].class, connection.nextMessage()));
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredWithACloseFrame() throws Exception {
        // an endpoint with an @OnClose method: the server answers the close frame once it ran
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/echo/handshake");
        assertEquals("open handshake", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
        assertEquals("close handshake 1000 bye", events().poll(TIMEOUT, TimeUnit.SECONDS));
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredOnceAnAsyncCloseHandlerCompleted() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/async-close");
        assertEquals("open", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
        assertEquals("closed 1000", events().poll(TIMEOUT, TimeUnit.SECONDS));
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredWithoutACloseHandler() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/subprotocol");
        assertEquals("subprotocol none", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theServerClosesWithACodeAndAReason() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/close");
        connection.send("now");
        WebSocketTestClient.Closed closed = connection.closed();
        assertEquals(4000, closed.code());
        assertEquals("done now", closed.reason());
    }

    @Test
    @Tag(WEBSOCKET_PING_PONG)
    void aPingIsAnsweredWithAPong() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/echo/ping");
        assertEquals("open ping", connection.next());
        connection.ws.sendPing(ByteBuffer.wrap("ping-data".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ping-data", connection.pongs.poll(TIMEOUT, TimeUnit.SECONDS));
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_PING_PONG)
    void thePongHandlerReceivesThePongs() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/pong");
        connection.ws.sendPong(ByteBuffer.wrap("pong-data".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("pong pong-data", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_SUBPROTOCOL)
    void theHandshakeSelectsASubprotocolOfTheEndpoint() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/subprotocol", b -> b.subprotocols("chat.v2", "chat.v1"));
        assertEquals("chat.v2", connection.ws.getSubprotocol());
        assertEquals("subprotocol chat.v2", connection.next());
        connection.close(1000, "bye");

        WebSocketTestClient.Connection other = client.connect("/ws/tck/subprotocol", b -> b.subprotocols("other"));
        assertEquals("", other.ws.getSubprotocol());
        assertEquals("subprotocol none", other.next());
        other.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_REQUEST_SCOPE)
    void theHandlersSeeTheUpgradeRequest() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/request?q=query", b -> b.header("X-Name", "header"));
        assertEquals("open /ws/tck/request header query true", connection.next());
        connection.send("message");
        assertEquals("message true", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ASYNC_HANDLERS)
    void aHandlerCanReturnAStage() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/stage");
        connection.send("later");
        assertEquals("LATER", connection.next());
        assertEquals("handled later", event());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ASYNC_HANDLERS)
    void aHandlerCanReturnAPublisher() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/publisher");
        connection.send("one");
        connection.send("two");
        assertEquals("ONE", connection.next());
        assertEquals("TWO", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ERROR_HANDLING)
    void aFailingHandlerReachesTheErrorHandler() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/error");
        connection.send("boom");
        assertEquals("error failed boom", connection.next());
    }

    @Test
    @Tag(WEBSOCKET_ERROR_HANDLING)
    void theErrorHandlerSeesAThrownErrorAndAFailedStage() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/failing");
        connection.send("throw");
        assertEquals("error thrown throw", connection.next());
        connection.send("stage");
        assertEquals("error failed stage", connection.next());
        // the error handler decided not to close the connection
        connection.send("fine");
        assertEquals("ok fine", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ERROR_HANDLING)
    void withoutAnErrorHandlerAFailingHandlerClosesTheConnection() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/unhandled");
        connection.send("boom");
        assertEquals(CloseReason.INTERNAL_ERROR.getCode(), connection.closed().code());
    }

    @Test
    @Tag(WEBSOCKET_MAX_PAYLOAD)
    void aMessageLargerThanTheMaximumClosesTheConnection() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/small");
        connection.send("small");
        assertEquals("small", connection.next());
        connection.send("larger than eight bytes");
        assertEquals(CloseReason.MESSAGE_TO_BIG.getCode(), connection.closed().code());
    }

    @Test
    @Tag(WEBSOCKET_UPGRADE_FILTER)
    void aFilterRejectsTheUpgradeRequest() throws Exception {
        assertEquals(403, client.handshakeStatus("/ws/tck/guarded", b -> { }));
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/guarded", b -> b.header("X-Allow", "yes"));
        assertEquals("allowed", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_HANDSHAKE_ERRORS)
    void anUpgradeWithoutAnEndpointIsNotFound() {
        assertEquals(404, client.handshakeStatus("/ws/tck/missing", b -> { }));
    }

    @Test
    @Tag(WEBSOCKET_HANDSHAKE_ERRORS)
    void aRequestThatIsNotAnUpgradeIsABadRequest() throws Exception {
        assertEquals(400, client.plainGetStatus("/ws/tck/echo/lobby"));
    }

    @Test
    @Tag(WEBSOCKET_BROADCAST)
    void theBroadcasterReachesTheSessionsOfItsPredicate() throws Exception {
        WebSocketTestClient.Connection first = client.connect("/ws/tck/room/red");
        assertEquals("joined red", first.next());
        WebSocketTestClient.Connection second = client.connect("/ws/tck/room/red");
        assertEquals("joined red", second.next());
        // the join of the second reaches the first
        assertEquals("joined red", first.next());
        WebSocketTestClient.Connection otherRoom = client.connect("/ws/tck/room/blue");
        assertEquals("joined blue", otherRoom.next());

        first.send("hello");
        assertEquals("red: hello", first.next());
        assertEquals("red: hello", second.next());
        assertNull(otherRoom.messages.poll(200, TimeUnit.MILLISECONDS));

        first.close(1000, "bye");
        second.close(1000, "bye");
        otherRoom.close(1000, "bye");
    }

    private static BlockingQueue<String> events() {
        return server.getApplicationContext().getBean(Events.class).events;
    }

    private static String event() throws InterruptedException {
        String event = events().poll(TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(event, "no event");
        return event;
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Events {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/echo/{room}")
    static class EchoSocket {
        private final Events events;

        EchoSocket(Events events) {
            this.events = events;
        }

        @OnOpen
        void onOpen(String room, WebSocketSession session) {
            session.sendSync("open " + room);
        }

        @OnMessage
        void onMessage(String room, String message, WebSocketSession session) {
            session.sendSync("echo " + room + " " + message);
        }

        @OnClose
        void onClose(String room, CloseReason reason) {
            events.events.add("close " + room + " " + reason.getCode() + " " + reason.getReason());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/binary")
    static class BinarySocket {
        @OnMessage
        void onMessage(byte[] message, WebSocketSession session) {
            byte[] reversed = new byte[message.length];
            for (int i = 0; i < message.length; i++) {
                reversed[i] = message[message.length - 1 - i];
            }
            session.sendSync(reversed);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/close")
    static class CloseSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.close(new CloseReason(4000, "done " + message));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket(value = "/ws/tck/subprotocol", subprotocols = "chat.v1,chat.v2")
    static class SubprotocolSocket {
        @OnOpen
        void onOpen(WebSocketSession session) {
            session.sendSync("subprotocol " + session.getSubprotocol().orElse("none"));
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/request")
    static class RequestSocket {
        @OnOpen
        void onOpen(WebSocketSession session, HttpRequest<?> request) {
            session.sendSync("open " + request.getPath() + " " + request.getHeaders().get("X-Name") + " "
                + request.getParameters().get("q") + " " + ServerRequestContext.currentRequest().isPresent());
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message + " " + ServerRequestContext.currentRequest().isPresent());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/stage")
    static class StageSocket {
        private final Events events;

        StageSocket(Events events) {
            this.events = events;
        }

        @OnMessage
        CompletionStage<Void> onMessage(String message, WebSocketSession session) {
            return CompletableFuture
                .supplyAsync(() -> message.toUpperCase(Locale.ROOT), CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
                .thenCompose(session::sendAsync)
                .thenRun(() -> events.events.add("handled " + message));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/async-close")
    static class AsyncCloseSocket {
        private final Events events;

        AsyncCloseSocket(Events events) {
            this.events = events;
        }

        @OnOpen
        void onOpen(WebSocketSession session) {
            session.sendSync("open");
        }

        @OnMessage
        void onMessage(String message) {
            // not used
        }

        @OnClose
        CompletionStage<Void> onClose(CloseReason reason) {
            return CompletableFuture.runAsync(
                () -> events.events.add("closed " + reason.getCode()),
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/publisher")
    static class PublisherSocket {
        @OnMessage
        Publisher<Void> onMessage(String message, WebSocketSession session) {
            return Mono.delay(Duration.ofMillis(20))
                .then(Mono.fromCompletionStage(session.sendAsync(message.toUpperCase(Locale.ROOT))))
                .then();
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/error")
    static class ErrorSocket {
        @OnMessage
        void onMessage(String message) {
            throw new IllegalStateException("failed " + message);
        }

        @OnError
        void onError(Throwable error, WebSocketSession session) {
            session.sendSync("error " + error.getMessage());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/failing")
    static class FailingSocket {
        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            return switch (message) {
                case "throw" -> throw new IllegalStateException("thrown " + message);
                case "stage" -> CompletableFuture.failedFuture(new IllegalStateException("failed " + message));
                default -> session.sendAsync("ok " + message);
            };
        }

        @OnError
        CompletionStage<?> onError(Throwable error, WebSocketSession session) {
            return session.sendAsync("error " + error.getMessage());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/unhandled")
    static class UnhandledSocket {
        @OnMessage
        void onMessage(String message) {
            throw new IllegalStateException("unhandled " + message);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/pong")
    static class PongSocket {
        @OnMessage
        void onMessage(String message) {
            // not used
        }

        @OnMessage
        void onPong(WebSocketPongMessage pong, WebSocketSession session) {
            session.sendSync("pong " + pong.getContent().toString(StandardCharsets.UTF_8));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/small")
    static class SmallSocket {
        @OnMessage(maxPayloadLength = 8)
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/guarded")
    static class GuardedSocket {
        @OnOpen
        void onOpen(WebSocketSession session) {
            session.sendSync("allowed");
        }

        @OnMessage
        void onMessage(String message) {
            // nothing to do
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/ws/tck/guarded")
    static class GuardFilter {
        @RequestFilter
        @Nullable
        HttpResponse<?> guard(HttpRequest<?> request) {
            return request.getHeaders().contains("X-Allow") ? null : HttpResponse.status(HttpStatus.FORBIDDEN);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/room/{room}")
    static class RoomSocket {
        private final WebSocketBroadcaster broadcaster;

        RoomSocket(WebSocketBroadcaster broadcaster) {
            this.broadcaster = broadcaster;
        }

        @OnOpen
        CompletionStage<?> onOpen(String room, WebSocketSession session) {
            return broadcaster.broadcastAsync("joined " + room, other -> sameRoom(room, other));
        }

        @OnMessage
        CompletionStage<?> onMessage(String room, String message, WebSocketSession session) {
            return broadcaster.broadcastAsync(room + ": " + message, other -> sameRoom(room, other));
        }

        private static boolean sameRoom(String room, WebSocketSession other) {
            return room.equals(other.getUriVariables().get("room", String.class).orElse(null));
        }
    }
}
