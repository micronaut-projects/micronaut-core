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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_ASYNC_HANDLERS;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_BINARY;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_BROADCAST;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_CLOSE_HANDSHAKE;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_ERROR_HANDLING;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_FRAGMENTATION;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_FUNCTIONAL_ROUTES;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_HANDSHAKE_ERRORS;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_MAX_PAYLOAD;
import static io.micronaut.http.server.tck.WebSocketTckTags.WEBSOCKET_PING_HANDLER;
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
 * WebSocket routes of handler functions declared with the route builder, driven by the JDK
 * WebSocket client. Each operation is tested like for the beans of {@link ServerWebSocketTest},
 * with the same tags, except where the APIs differ:
 * <ul>
 *     <li>a handler returns a stage, not a publisher: a route replies with the messages of a
 *     publisher through {@link WebSocketSession#sendAllAsync(org.reactivestreams.Publisher)};</li>
 *     <li>the message stream handler, the ping handler, and a route without a message handler
 *     have no counterpart for beans, which have one {@code @OnMessage} method for the messages
 *     and receive no pings.</li>
 * </ul>
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
@Tag(WEBSOCKET)
@Tag(WEBSOCKET_FUNCTIONAL_ROUTES)
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class FunctionalWebSocketRouteTest {
    public static final String SPEC_NAME = "FunctionalWebSocketRouteTest";

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
    void theHandlersSeeThePathVariables() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/chat/lobby");
        assertEquals("open lobby", connection.next());
        connection.send("hello");
        assertEquals("echo lobby hello", connection.next());
        connection.close(1000, "bye");
        assertEquals("close lobby 1000 bye", event());
    }

    @Test
    @Tag(WEBSOCKET_REQUEST_SCOPE)
    void theHandlersSeeTheUpgradeRequestAndTheCurrentRequest() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/request", b -> b.header("X-Name", "header"));
        assertEquals("open /ws/tck/request header true", connection.next());
        connection.send("message");
        assertEquals("message true", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_FRAGMENTATION)
    void aMessageInFragmentsIsOneMessage() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/chat/fragments");
        assertEquals("open fragments", connection.next());
        connection.ws.sendText("hel", false).get(TIMEOUT, TimeUnit.SECONDS);
        connection.ws.sendText("lo", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo fragments hello", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_SUBPROTOCOL)
    void theHandshakeSelectsASubprotocolOfTheRoute() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/chat/lobby", b -> b.subprotocols("chat.v2", "chat.v1"));
        assertEquals("chat.v2", connection.ws.getSubprotocol());
        assertEquals("open lobby", connection.next());
        connection.close(1000, "bye");

        WebSocketTestClient.Connection other = client.connect("/ws/tck/chat/lobby", b -> b.subprotocols("other"));
        assertEquals("", other.ws.getSubprotocol());
        assertEquals("open lobby", other.next());
        other.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_BINARY)
    void aBinaryMessageIsBytesAndTheReplyBinary() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/binary");
        connection.ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true).get(TIMEOUT, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{3, 2, 1}, assertInstanceOf(byte[].class, connection.nextMessage()));
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ASYNC_HANDLERS)
    void aHandlerCanReplyWithAStage() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/async");
        connection.send("later");
        assertEquals("LATER", connection.next());
        assertEquals("handled later", event());
        connection.close(1000, "bye");
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
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void aHandlerClosesTheConnectionWithItsCode() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/close");
        connection.send("now");
        WebSocketTestClient.Closed closed = connection.closed();
        assertEquals(4000, closed.code());
        assertEquals("done now", closed.reason());
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredWithACloseFrameOnceTheCloseHandlerIsDone() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/chat/answered");
        assertEquals("open answered", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
        assertEquals("close answered 1000 bye", event());
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredOnceAnAsyncCloseHandlerCompleted() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/async-close");
        assertEquals("open", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
        assertEquals("closed 1000", event());
    }

    @Test
    @Tag(WEBSOCKET_CLOSE_HANDSHAKE)
    void theCloseOfTheClientIsAnsweredWithoutACloseHandler() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/push");
        assertEquals("pushed", connection.next());
        WebSocketTestClient.Closed closed = connection.close(1000, "bye");
        assertEquals(1000, closed.code());
        assertEquals("bye", closed.reason());
    }

    @Test
    @Tag(WEBSOCKET_ASYNC_HANDLERS)
    void aHandlerCanReplyWithTheMessagesOfAPublisher() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/publisher");
        connection.send("one");
        connection.send("two");
        assertEquals("ONE", connection.next());
        assertEquals("one!", connection.next());
        assertEquals("TWO", connection.next());
        assertEquals("two!", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_ASYNC_HANDLERS)
    void aMessageStreamHandlerReceivesTheMessagesAsAPublisher() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/stream");
        connection.send("one");
        connection.send("two");
        assertEquals("ONE", connection.next());
        assertEquals("TWO", connection.next());
        connection.close(1000, "bye");
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
    void aRouteWithoutAMessageHandlerPushesAndRejectsMessages() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/push");
        assertEquals("pushed", connection.next());
        connection.send("unexpected");
        assertEquals(CloseReason.UNSUPPORTED_DATA.getCode(), connection.closed().code());
    }

    @Test
    @Tag(WEBSOCKET_PING_PONG)
    @Tag(WEBSOCKET_PING_HANDLER)
    void thePingHandlerReceivesThePingsWhichAreAnswered() throws Exception {
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/ping");
        connection.ws.sendPing(ByteBuffer.wrap("ping-data".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ping ping-data", connection.next());
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
    @Tag(WEBSOCKET_UPGRADE_FILTER)
    void theFiltersOfTheRouteApplyToTheUpgradeRequest() throws Exception {
        assertEquals(403, client.handshakeStatus("/ws/tck/guarded", b -> { }));
        WebSocketTestClient.Connection connection = client.connect("/ws/tck/guarded", b -> b.header("X-Allow", "yes"));
        assertEquals("allowed", connection.next());
        connection.close(1000, "bye");
    }

    @Test
    @Tag(WEBSOCKET_HANDSHAKE_ERRORS)
    void anUpgradeWithoutARouteIsNotFound() {
        assertEquals(404, client.handshakeStatus("/ws/tck/missing", b -> { }));
    }

    @Test
    @Tag(WEBSOCKET_HANDSHAKE_ERRORS)
    void aRequestThatIsNotAnUpgradeIsABadRequest() throws Exception {
        assertEquals(400, client.plainGetStatus("/ws/tck/chat/lobby"));
    }

    @Test
    @Tag(WEBSOCKET_BROADCAST)
    void theBroadcasterReachesTheSessionsOfRoutesAndOfServerWebSocketBeans() throws Exception {
        WebSocketTestClient.Connection annotated = client.connect("/ws/tck/annotated/red");
        // the session of the bean is open once its broadcast reaches it
        annotated.send("ready");
        assertEquals("ready", annotated.next());
        WebSocketTestClient.Connection functional = client.connect("/ws/tck/broadcast/red");
        WebSocketTestClient.Connection otherRoom = client.connect("/ws/tck/broadcast/blue");
        // the open sessions are the sessions of both kinds of endpoints
        assertEquals("open sessions 2 red", functional.next());

        functional.send("from route");
        assertEquals("from route", annotated.next());
        assertEquals("from route", functional.next());

        annotated.send("from bean");
        assertEquals("from bean", functional.next());
        assertEquals("from bean", annotated.next());

        assertEquals("open sessions 1 blue", otherRoom.next());
        assertNull(otherRoom.messages.poll(200, TimeUnit.MILLISECONDS));

        annotated.close(1000, "bye");
        functional.close(1000, "bye");
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

    private static String room(WebSocketSession session) {
        return session.getUriVariables().get("room", String.class).orElseThrow();
    }

    private static Predicate<WebSocketSession> sameRoom(WebSocketSession session) {
        String room = room(session);
        return other -> room.equals(other.getUriVariables().get("room", String.class).orElse(null));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Events implements AutoCloseable {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        /**
         * The thread of the delays, owned by this test: Reactor's shared parallel scheduler and
         * the common fork join pool would start their threads here, and they would keep the
         * leak detection resource scope of this test class for every later test that runs on them.
         */
        private final ScheduledExecutorService delays = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, SPEC_NAME + "-delays");
            thread.setDaemon(true);
            return thread;
        });
        final Scheduler delayScheduler = Schedulers.fromExecutorService(delays);

        Executor delayed(long millis) {
            return CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS, delays);
        }

        @PreDestroy
        @Override
        public void close() {
            delayScheduler.dispose();
            delays.shutdownNow();
        }
    }

    /**
     * A {@code @ServerWebSocket} bean the broadcaster reaches together with the routes.
     */
    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws/tck/annotated/{room}")
    static class AnnotatedRoom {
        private final WebSocketBroadcaster broadcaster;

        AnnotatedRoom(WebSocketBroadcaster broadcaster) {
            this.broadcaster = broadcaster;
        }

        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            return broadcaster.broadcastAsync(message, sameRoom(session));
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes webSocketRoutes(Events events, BeanProvider<WebSocketBroadcaster> broadcaster) {
            BlockingQueue<String> log = events.events;
            return routes -> {
                routes.GET("/ws/tck/chat/{room}").webSocket(ws -> ws
                    .subprotocols("chat.v1", "chat.v2")
                    .onOpen((session, request) -> session.sendAsync("open " + room(session)))
                    .onMessage(String.class, (session, message) -> session.sendAsync("echo " + room(session) + " " + message))
                    .onClose((session, reason) -> {
                        log.add("close " + room(session) + " " + reason.getCode() + " " + reason.getReason());
                        return null;
                    }));
                routes.GET("/ws/tck/request").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("open " + request.getPath() + " " + request.getHeaders().get("X-Name")
                        + " " + ServerRequestContext.currentRequest().isPresent()))
                    .onMessage(String.class, (session, message) -> session.sendAsync(message + " "
                        + ServerRequestContext.currentRequest().isPresent())));
                routes.GET("/ws/tck/binary").webSocket(ws -> ws
                    .onMessage(byte[].class, (session, bytes) -> {
                        byte[] reversed = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) {
                            reversed[i] = bytes[bytes.length - 1 - i];
                        }
                        return session.sendAsync(reversed);
                    }));
                routes.GET("/ws/tck/async").webSocket(ws -> ws
                    .onMessage(String.class, (session, message) -> CompletableFuture
                        .supplyAsync(() -> message.toUpperCase(Locale.ROOT), events.delayed(50))
                        .thenCompose(session::sendAsync)
                        .thenRun(() -> log.add("handled " + message))));
                routes.GET("/ws/tck/failing").webSocket(ws -> ws
                    .onMessage(String.class, (session, message) -> switch (message) {
                        case "throw" -> throw new IllegalStateException("thrown " + message);
                        case "stage" -> CompletableFuture.failedFuture(new IllegalStateException("failed " + message));
                        default -> session.sendAsync("ok " + message);
                    })
                    .onError((session, error) -> session.sendAsync("error " + error.getMessage())));
                routes.GET("/ws/tck/unhandled").webSocket(ws -> ws
                    .onMessage(String.class, (session, message) -> {
                        throw new IllegalStateException("unhandled " + message);
                    }));
                routes.GET("/ws/tck/close").webSocket(ws -> ws
                    .onMessage(String.class, (session, message) -> {
                        session.close(new CloseReason(4000, "done " + message));
                        return null;
                    }));
                routes.GET("/ws/tck/async-close").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("open"))
                    .onMessage(String.class, (session, message) -> null)
                    .onClose((session, reason) -> CompletableFuture.runAsync(
                        () -> log.add("closed " + reason.getCode()),
                        events.delayed(50))));
                routes.GET("/ws/tck/publisher").webSocket(ws -> ws
                    .onMessage(String.class, (session, message) -> session.sendAllAsync(
                        Flux.just(message.toUpperCase(Locale.ROOT), message + "!").delayElements(Duration.ofMillis(10), events.delayScheduler))));
                routes.GET("/ws/tck/stream").webSocket(ws -> ws
                    .onMessageStream(String.class, (session, messages) ->
                        session.sendAllAsync(Flux.from(messages).map(message -> message.toUpperCase(Locale.ROOT)))));
                routes.GET("/ws/tck/small").webSocket(ws -> ws
                    .maxPayloadLength(8)
                    .onMessage(String.class, (session, message) -> session.sendAsync(message)));
                routes.GET("/ws/tck/push").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("pushed")));
                routes.GET("/ws/tck/ping").webSocket(ws -> ws
                    .onPing((session, ping) -> session.sendAsync("ping " + ping.getContent().toString(StandardCharsets.UTF_8))));
                routes.GET("/ws/tck/pong").webSocket(ws -> ws
                    .onPong((session, pong) -> session.sendAsync("pong " + pong.getContent().toString(StandardCharsets.UTF_8))));
                routes.GET("/ws/tck/guarded")
                    .beforeReplacing(request -> request.getHeaders().contains("X-Allow") ? null : HttpResponse.status(HttpStatus.FORBIDDEN)).and()
                    .webSocket(ws -> ws
                        .onOpen((session, request) -> session.sendAsync("allowed")));
                routes.GET("/ws/tck/broadcast/{room}").webSocket(ws -> ws
                    .onOpen((session, request) -> {
                        long open = session.getOpenSessions().stream().filter(sameRoom(session)).count();
                        return session.sendAsync("open sessions " + open + " " + room(session));
                    })
                    .onMessage(String.class, (session, message) -> broadcaster.get().broadcastAsync(message, sameRoom(session))));
            };
        }
    }
}
