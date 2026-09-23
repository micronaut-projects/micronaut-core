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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket routes of handler functions, declared with the route builder, driven by the JDK
 * WebSocket client, which is {@link CompletionStage} based like the handlers.
 */
class HandlerRouteWebSocketTest {

    private static final String SPEC = "HandlerRouteWebSocketTest";
    private static final long TIMEOUT = 10;

    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static HttpClient http;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1));
        server = context.getBean(EmbeddedServer.class).start();
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        http.close();
        context.close();
    }

    @BeforeEach
    void clearEvents() {
        context.getBean(Events.class).events.clear();
    }

    @Test
    void theHandlersSeeThePathVariablesTheUpgradeRequestAndTheCurrentRequest() throws Exception {
        Client client = connect("/ws/chat/lobby");
        assertEquals("open lobby /ws/chat/lobby true", client.next());

        client.ws.sendText("hello", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo lobby hello true", client.next());

        // a message in fragments is one message
        client.ws.sendText("hel", false).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("lo again", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo lobby hello again true", client.next());

        client.close(1000, "bye");
        assertEquals("close lobby 1000 bye", event());
    }

    @Test
    void theHandshakeSelectsASubprotocolOfTheRoute() throws Exception {
        Client client = connect("/ws/chat/lobby", builder -> builder.subprotocols("chat.v2", "chat.v1"));
        assertEquals("chat.v2", client.ws.getSubprotocol());
        client.next();
        client.ws.sendText("subprotocol", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo lobby subprotocol true", client.next());
        client.close(1000, "done");

        // not a subprotocol of the route
        Client other = connect("/ws/chat/lobby", builder -> builder.subprotocols("other"));
        assertEquals("", other.ws.getSubprotocol());
        other.close(1000, "done");
    }

    @Test
    void aTypedMessageIsDecodedFromJsonAndTheReplyEncoded() throws Exception {
        Client client = connect("/ws/json");
        client.ws.sendText("{\"text\":\"hello\",\"count\":1}", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("{\"text\":\"HELLO\",\"count\":2}", client.next());
        client.close(1000, "done");
    }

    @Test
    void aBinaryMessageIsBytesAndTheReplyBinary() throws Exception {
        Client client = connect("/ws/binary");
        client.ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3}), true).get(TIMEOUT, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{3, 2, 1}, assertInstanceOf(byte[].class, client.nextMessage()));
        client.close(1000, "done");
    }

    @Test
    void aHandlerCanReplyWithAStage() throws Exception {
        Client client = connect("/ws/async");
        client.ws.sendText("later", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("LATER", client.next());
        assertEquals("handled later", event());
        client.close(1000, "done");
    }

    @Test
    void theErrorHandlerSeesAThrownErrorAndAFailedStage() throws Exception {
        Client client = connect("/ws/failing");
        client.ws.sendText("throw", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("error thrown throw", client.next());
        client.ws.sendText("stage", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("error failed stage", client.next());
        // the error handler decided not to close the connection
        client.ws.sendText("fine", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ok fine", client.next());
        client.close(1000, "done");
    }

    @Test
    void withoutAnErrorHandlerAFailingHandlerClosesTheConnection() throws Exception {
        Client client = connect("/ws/unhandled");
        client.ws.sendText("boom", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(CloseReason.INTERNAL_ERROR.getCode(), client.closed().code);
    }

    @Test
    void aFailingOpenHandlerClosesTheConnectionAndReachesTheErrorHandler() throws Exception {
        Client client = connect("/ws/failing-open");
        assertEquals(CloseReason.INTERNAL_ERROR.getCode(), client.closed().code);
        assertEquals("open error open failed", event());
    }

    @Test
    void aHandlerClosesTheConnectionWithItsCode() throws Exception {
        Client client = connect("/ws/close");
        client.ws.sendText("now", true).get(TIMEOUT, TimeUnit.SECONDS);
        Closed closed = client.closed();
        assertEquals(4000, closed.code);
        assertEquals("done now", closed.reason);
    }

    @Test
    void aMessageLargerThanTheMaximumClosesTheConnection() throws Exception {
        Client client = connect("/ws/small");
        client.ws.sendText("small", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("small", client.next());
        client.ws.sendText("larger than eight bytes", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(CloseReason.MESSAGE_TO_BIG.getCode(), client.closed().code);
    }

    @Test
    void aRouteWithoutAMessageHandlerPushesAndRejectsMessages() throws Exception {
        Client client = connect("/ws/push");
        assertEquals("pushed", client.next());
        client.ws.sendText("unexpected", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(CloseReason.UNSUPPORTED_DATA.getCode(), client.closed().code);
    }

    @Test
    void thePongHandlerReceivesThePongs() throws Exception {
        Client client = connect("/ws/pong");
        client.ws.sendPong(ByteBuffer.wrap("ping-data".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("pong ping-data", client.next());
        client.close(1000, "done");
    }

    @Test
    void theFiltersOfTheRouteApplyToTheUpgradeRequest() throws Exception {
        assertEquals(403, handshakeStatus("/ws/guarded", builder -> { }));
        Client client = connect("/ws/guarded", builder -> builder.header("X-Allow", "yes"));
        assertEquals("allowed", client.next());
        client.close(1000, "done");
    }

    @Test
    void theFiltersAndAttributesOfTheGroupApplyToTheUpgradeRequest() throws Exception {
        assertEquals(403, handshakeStatus("/ws/group/7", builder -> { }));
        Client client = connect("/ws/group/7", builder -> builder.header("X-Group", "yes"));
        assertEquals("group 7 grouped", client.next());
        client.close(1000, "done");
    }

    @Test
    void theConditionsOfTheRouteSelectTheUpgradeRequests() throws Exception {
        assertEquals(404, handshakeStatus("/ws/where", builder -> builder.header("X-Variant", "a")));
        Client client = connect("/ws/where", builder -> builder.header("X-Variant", "b"));
        assertEquals("where", client.next());
        client.close(1000, "done");
    }

    @Test
    void anUpgradeWithoutARouteIsNotFound() throws Exception {
        assertEquals(404, handshakeStatus("/ws/missing", builder -> { }));
    }

    @Test
    void aRequestThatIsNotAnUpgradeIsABadRequest() throws Exception {
        var response = http.send(HttpRequest.newBuilder(server.getURI().resolve("/ws/chat/lobby")).GET().build(), BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
    }

    @Test
    void theExecutorOfTheRouteRunsTheHandlers() throws Exception {
        Client client = connect("/ws/executor");
        String open = client.next();
        assertTrue(open.startsWith("open on "), open);
        assertFalse(open.toLowerCase(Locale.ROOT).contains("eventloop"), open);
        client.ws.sendText("message", true).get(TIMEOUT, TimeUnit.SECONDS);
        String message = client.next();
        assertTrue(message.startsWith("message on "), message);
        assertFalse(message.toLowerCase(Locale.ROOT).contains("eventloop"), message);
        client.close(1000, "done");

        // without an executor, the handlers run on the event loop, like a method that returns a stage
        Client eventLoop = connect("/ws/event-loop");
        String thread = eventLoop.next();
        assertTrue(thread.toLowerCase(Locale.ROOT).contains("eventloop"), thread);
        eventLoop.close(1000, "done");
    }

    @Test
    void theBroadcasterReachesTheSessionsOfRoutesAndOfServerWebSocketBeans() throws Exception {
        Client annotated = connect("/ws/annotated/red");
        // the session of the bean is open once its broadcast reaches it
        annotated.ws.sendText("ready", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ready", annotated.next());
        Client functional = connect("/ws/broadcast/red");
        Client otherRoom = connect("/ws/broadcast/blue");
        // the open sessions are the sessions of both kinds of endpoints
        assertEquals("open sessions 2 red", functional.next());

        functional.ws.sendText("from route", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("from route", annotated.next());
        assertEquals("from route", functional.next());

        annotated.ws.sendText("from bean", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("from bean", functional.next());
        assertEquals("from bean", annotated.next());

        assertEquals("open sessions 1 blue", otherRoom.next());
        assertNull(otherRoom.messages.poll(200, TimeUnit.MILLISECONDS));

        annotated.close(1000, "done");
        functional.close(1000, "done");
        otherRoom.close(1000, "done");
    }

    private static String event() throws InterruptedException {
        String event = context.getBean(Events.class).events.poll(TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(event, "no event");
        return event;
    }

    private static Client connect(String path) throws Exception {
        return connect(path, builder -> { });
    }

    private static Client connect(String path, Consumer<WebSocket.Builder> customizer) throws Exception {
        Client client = new Client();
        WebSocket.Builder builder = http.newWebSocketBuilder();
        customizer.accept(builder);
        client.ws = builder.buildAsync(uri(path), client).get(TIMEOUT, TimeUnit.SECONDS);
        return client;
    }

    private static int handshakeStatus(String path, Consumer<WebSocket.Builder> customizer) throws Exception {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        customizer.accept(builder);
        ExecutionException error = assertThrows(ExecutionException.class,
            () -> builder.buildAsync(uri(path), new Client()).get(TIMEOUT, TimeUnit.SECONDS));
        return assertInstanceOf(WebSocketHandshakeException.class, error.getCause()).getResponse().statusCode();
    }

    private static URI uri(String path) {
        return URI.create("ws://localhost:" + server.getPort() + path);
    }

    private static String room(WebSocketSession session) {
        return session.getUriVariables().get("room", String.class).orElseThrow();
    }

    private static byte[] reverse(byte[] bytes) {
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            reversed[i] = bytes[bytes.length - 1 - i];
        }
        return reversed;
    }

    /**
     * A message of the JSON route.
     *
     * @param text  The text
     * @param count The count
     */
    record Msg(String text, int count) {
    }

    record Closed(int code, String reason) {
    }

    /**
     * Collects the messages of a connection.
     */
    static final class Client implements WebSocket.Listener {

        final BlockingQueue<Object> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Closed> closed = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        WebSocket ws;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binary.writeBytes(bytes);
            if (last) {
                messages.add(binary.toByteArray());
                binary.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(new Closed(statusCode, reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.completeExceptionally(error);
        }

        Object nextMessage() throws InterruptedException {
            Object message = messages.poll(TIMEOUT, TimeUnit.SECONDS);
            assertNotNull(message, "no message");
            return message;
        }

        String next() throws InterruptedException {
            return assertInstanceOf(String.class, nextMessage());
        }

        Closed closed() throws Exception {
            return closed.get(TIMEOUT, TimeUnit.SECONDS);
        }

        void close(int code, String reason) throws Exception {
            ws.sendClose(code, reason).get(TIMEOUT, TimeUnit.SECONDS);
            // the server closes the connection when its close handler is done, like for a bean
            closed.handle((c, e) -> c).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    /**
     * What the handlers report.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Events {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    }

    /**
     * A {@code @ServerWebSocket} bean the broadcaster reaches together with the routes.
     */
    @Requires(property = "spec.name", value = SPEC)
    @ServerWebSocket("/ws/annotated/{room}")
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

    private static Predicate<WebSocketSession> sameRoom(WebSocketSession session) {
        String room = room(session);
        return other -> room.equals(other.getUriVariables().get("room", String.class).orElse(null));
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class Routes {

        @Singleton
        HttpRoutes webSocketRoutes(Events events, BeanProvider<WebSocketBroadcaster> broadcaster) {
            BlockingQueue<String> log = events.events;
            return routes -> {
                routes.webSocket("/ws/chat/{room}", ws -> ws
                    .subprotocols("chat.v1", "chat.v2")
                    .onOpen((session, request) -> session.sendAsync("open " + room(session) + " " + request.getPath()
                        + " " + ServerRequestContext.currentRequest().isPresent()))
                    .onMessage(String.class, (message, session) -> session.sendAsync("echo " + room(session) + " " + message
                        + " " + ServerRequestContext.currentRequest().isPresent()))
                    .onClose((reason, session) -> {
                        log.add("close " + room(session) + " " + reason.getCode() + " " + reason.getReason());
                        return null;
                    }));

                routes.webSocket("/ws/json", ws -> ws
                    .onMessage(Argument.of(Msg.class), (message, session) ->
                        session.sendAsync(new Msg(message.text().toUpperCase(Locale.ROOT), message.count() + 1))));

                routes.webSocket("/ws/binary", ws -> ws
                    .onMessage(byte[].class, (bytes, session) -> session.sendAsync(reverse(bytes))));

                routes.webSocket("/ws/async", ws -> ws
                    .onMessage(String.class, (message, session) -> CompletableFuture
                        .supplyAsync(() -> message.toUpperCase(Locale.ROOT), CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
                        .thenCompose(session::sendAsync)
                        .thenRun(() -> log.add("handled " + message))));

                routes.webSocket("/ws/failing", ws -> ws
                    .onMessage(String.class, (message, session) -> switch (message) {
                        case "throw" -> throw new IllegalStateException("thrown " + message);
                        case "stage" -> CompletableFuture.failedFuture(new IllegalStateException("failed " + message));
                        default -> session.sendAsync("ok " + message);
                    })
                    .onError((error, session) -> session.sendAsync("error " + error.getMessage())));

                routes.webSocket("/ws/unhandled", ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        throw new IllegalStateException("unhandled " + message);
                    }));

                routes.webSocket("/ws/failing-open", ws -> ws
                    .onOpen((session, request) -> {
                        throw new IllegalStateException("open failed");
                    })
                    .onError((error, session) -> {
                        log.add("open error " + error.getMessage());
                        return null;
                    }));

                routes.webSocket("/ws/close", ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        session.close(new CloseReason(4000, "done " + message));
                        return null;
                    }));

                routes.webSocket("/ws/small", ws -> ws
                    .maxPayloadLength(8)
                    .onMessage(String.class, (message, session) -> session.sendAsync(message)));

                routes.webSocket("/ws/push", ws -> ws
                    .onOpen((session, request) -> session.sendAsync("pushed")));

                routes.webSocket("/ws/pong", ws -> ws
                    .onPong((pong, session) -> session.sendAsync("pong " + pong.getContent().toString(StandardCharsets.UTF_8))));

                routes.webSocket("/ws/guarded", ws -> ws
                        .onOpen((session, request) -> session.sendAsync("allowed")))
                    .before(request -> request.getHeaders().contains("X-Allow") ? null : HttpResponse.status(HttpStatus.FORBIDDEN));

                routes.path("/ws/group", group -> {
                    group.before(request -> request.getHeaders().contains("X-Group") ? null : HttpResponse.status(HttpStatus.FORBIDDEN));
                    group.webSocket("/{id}", ws -> ws
                            .onOpen((session, request) -> session.sendAsync("group " + session.getUriVariables().get("id", String.class).orElseThrow()
                                + " " + RouteAttributes.getRouteInfo(request).flatMap(route -> route.getAttribute("kind", String.class)).orElse("-"))))
                        .attribute("kind", "grouped");
                });

                routes.webSocket("/ws/where", ws -> ws
                        .onOpen((session, request) -> session.sendAsync("where")))
                    .where(RequestPredicates.header("X-Variant", "b"));

                routes.webSocket("/ws/executor", ws -> ws
                        .onOpen((session, request) -> session.sendAsync("open on " + Thread.currentThread().getName()))
                        .onMessage(String.class, (message, session) -> session.sendAsync(message + " on " + Thread.currentThread().getName())))
                    .executeOn(TaskExecutors.BLOCKING);

                routes.webSocket("/ws/event-loop", ws -> ws
                    .onOpen((session, request) -> session.sendAsync(Thread.currentThread().getName())));

                routes.webSocket("/ws/broadcast/{room}", ws -> ws
                    .onOpen((session, request) -> {
                        long open = session.getOpenSessions().stream().filter(sameRoom(session)).count();
                        return session.sendAsync("open sessions " + open + " " + room(session));
                    })
                    .onMessage(String.class, (message, session) -> broadcaster.get().broadcastAsync(message, sameRoom(session))));
            };
        }
    }
}
