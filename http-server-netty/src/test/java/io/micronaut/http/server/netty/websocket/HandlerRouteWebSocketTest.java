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
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.web.router.builder.RouteSpec;
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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    /**
     * The port of the route of its own port, see {@link RouteSpec#port(int)}.
     */
    private static int routePort;

    @BeforeAll
    static void start() {
        routePort = SocketUtils.findAvailableTcpPort();
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
    void theClosestRouteIsUpgradedBeforeARouteOfALowerOrder() throws Exception {
        // like for any other request: the literal route, though the variable one has a lower order
        Client literal = connect("/ws/closest/fixed");
        assertEquals("literal", literal.next());
        literal.close(1000, "done");

        Client variable = connect("/ws/closest/other");
        assertEquals("variable other", variable.next());
        variable.close(1000, "done");

        // of equally close routes, the one of the lowest order
        Client ordered = connect("/ws/ordered");
        assertEquals("lower order", ordered.next());
        ordered.close(1000, "done");
    }

    @Test
    void theRouteOfAPortIsUpgradedOnItsPortOnly() throws Exception {
        Client client = connect(uri(routePort, "/ws/port"), builder -> { });
        assertEquals("port " + routePort, client.next());
        client.close(1000, "done");
        assertEquals(404, handshakeStatus(uri(server.getPort(), "/ws/port"), builder -> { }));

        // a route without a port is a route of the default ports, not of the port of another route
        assertEquals(404, handshakeStatus(uri(routePort, "/ws/push"), builder -> { }));
    }

    @Test
    void theOpenHandlerSendsAStream() throws Exception {
        Client client = connect("/ws/ticks/3");
        assertEquals("tick 1", client.next());
        assertEquals("tick 2", client.next());
        assertEquals("tick 3", client.next());
        assertEquals("sent 3 ticks", event());
        client.close(1000, "done");
    }

    @Test
    void theRepliesToAMessageAreAStream() throws Exception {
        Client client = connect("/ws/replies");
        client.ws.sendText("reply", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("reply 1", client.next());
        assertEquals("reply 2", client.next());
        assertEquals("reply 3", client.next());
        client.close(1000, "done");
    }

    @Test
    void aStreamThatDoesNotEndIsCancelledWhenTheConnectionCloses() throws Exception {
        Client client = connect("/ws/endless");
        assertEquals("endless 1", client.next());
        // the stream does not hold the messages back: it was not returned
        client.ws.sendText("still read", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("echo still read", client.next());
        client.close(1000, "done");
        assertEquals("endless cancelled", event());
    }

    @Test
    void theMessagesOfAConnectionAreHandledOneAfterTheOther() throws Exception {
        Client client = connect("/ws/serial");
        client.ws.sendText("a", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("b", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("done a", client.next());
        assertEquals("done b", client.next());
        assertEquals("start a", event());
        assertEquals("end a", event());
        assertEquals("start b", event());
        assertEquals("end b", event());
        client.close(1000, "done");
    }

    @Test
    void theMessagesOfAConnectionAreHandledConcurrentlyUpToTheMaximum() throws Exception {
        Client client = connect("/ws/concurrent");
        // the first is done once the second arrives: one at a time, it never would be
        client.ws.sendText("first", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("second", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("second done", client.next());
        assertEquals("first done", client.next());
        client.close(1000, "done");
    }

    @Test
    void theFirstMessageIsHandledOnceTheOpenHandlerIsDone() throws Exception {
        Client client = connect("/ws/open-first");
        client.ws.sendText("x", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("got x", client.next());
        assertEquals("opened", event());
        assertEquals("message x", event());
        client.close(1000, "done");
    }

    @Test
    void theMessagesHandlerReceivesTheMessagesAsAStream() throws Exception {
        Client client = connect("/ws/upper");
        client.ws.sendText("a", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("b", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("c", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("A", client.next());
        assertEquals("B", client.next());
        assertEquals("C", client.next());
        client.close(1000, "done");
        // the stream completes when the connection closes
        assertEquals("upper complete", event());
    }

    @Test
    void aSubscriberThatCancelsDiscardsTheMessagesThatFollow() throws Exception {
        Client client = connect("/ws/first");
        client.ws.sendText("a", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("first a", client.next());
        client.ws.sendText("b", true).get(TIMEOUT, TimeUnit.SECONDS);
        // the connection reads on: the ping after the discarded message is handled
        client.ws.sendPing(ByteBuffer.wrap("p".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ping p", client.next());
        client.close(1000, "done");
    }

    @Test
    void aFailingMessagesHandlerReachesTheErrorHandler() throws Exception {
        Client client = connect("/ws/messages-failing");
        assertEquals("error messages failed", event());
        // its messages are discarded: the connection is not held back
        client.ws.sendText("discarded", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.ws.sendText("discarded too", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.close(1000, "done");
    }

    @Test
    void aStreamOfTheOpenHandlerDoesNotHoldThePingsAndTheCloseBack() throws Exception {
        Client client = connect("/ws/held");
        assertEquals("held 1", client.next());
        client.ws.sendPing(ByteBuffer.wrap("still".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("still", client.pongs.poll(TIMEOUT, TimeUnit.SECONDS));
        client.close(1000, "done");
        assertEquals("held cancelled", event());
    }

    @Test
    void aHandlerThatRunsDoesNotHoldThePingsBack() throws Exception {
        Client client = connect("/ws/stuck");
        client.ws.sendText("stuck", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("stuck", event());
        client.ws.sendPing(ByteBuffer.wrap("alive".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("alive", client.pongs.poll(TIMEOUT, TimeUnit.SECONDS));
        client.ws.abort();
    }

    @Test
    void aCloseIsHandledAtOnceAndDiscardsTheMessagesThatWait() throws Exception {
        Client client = connect("/ws/close-discards");
        client.ws.sendText("a", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("start a", event());
        // waits for the handler of a, which does not complete
        client.ws.sendText("b", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.close(1000, "done");
        assertEquals("close 1000", event());
        assertNull(context.getBean(Events.class).events.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void aCloseIsNotHeldBackByTheMessagesThatWaitForTheOpenHandler() throws Exception {
        Client client = connect("/ws/held");
        assertEquals("held 1", client.next());
        // waits for the open handler, whose stream ends when the connection closes
        client.ws.sendText("waits", true).get(TIMEOUT, TimeUnit.SECONDS);
        client.close(1000, "done");
        assertEquals("held cancelled", event());
    }

    @Test
    void theMessagesHandlerRunsOnTheExecutorOfTheRouteOnceTheOpenHandlerIsDone() throws Exception {
        // the executor of the route redispatches from non-blocking threads only, see
        // HttpServerConfiguration#isRedispatchNonBlockingOnly: not on the event loop
        Client blocking = connect("/ws/async-open/blocking");
        String thread = blocking.next();
        assertFalse(thread.toLowerCase(Locale.ROOT).contains("eventloop"), thread);
        assertTrue(thread.endsWith(" true"), thread);
        // the subscriber too, with the upgrade request as the current request
        blocking.ws.sendText("message", true).get(TIMEOUT, TimeUnit.SECONDS);
        String subscriber = blocking.next();
        assertFalse(subscriber.toLowerCase(Locale.ROOT).contains("eventloop"), subscriber);
        assertTrue(subscriber.endsWith(" true"), subscriber);
        blocking.close(1000, "done");

        Client eventLoop = connect("/ws/async-open/event-loop");
        thread = eventLoop.next();
        assertTrue(thread.toLowerCase(Locale.ROOT).contains("eventloop"), thread);
        assertTrue(thread.endsWith(" true"), thread);
        eventLoop.ws.sendText("message", true).get(TIMEOUT, TimeUnit.SECONDS);
        subscriber = eventLoop.next();
        assertTrue(subscriber.toLowerCase(Locale.ROOT).contains("eventloop"), subscriber);
        assertTrue(subscriber.endsWith(" true"), subscriber);
        eventLoop.close(1000, "done");
    }

    @Test
    void theFirstMessageIsHandledOnceTheOpenHandlerIsDoneWhateverTheMaximum() throws Exception {
        Client client = connect("/ws/open-first-concurrent");
        client.ws.sendText("x", true).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("got x", client.next());
        assertEquals("opened", event());
        assertEquals("message x", event());
        client.close(1000, "done");
    }

    @Test
    void theStreamReceivesTheMessagesInOrderOnAnExecutorOfManyThreads() throws Exception {
        Client client = connect("/ws/upper-blocking");
        for (int i = 1; i <= 20; i++) {
            client.ws.sendText("m" + i, true).get(TIMEOUT, TimeUnit.SECONDS);
        }
        for (int i = 1; i <= 20; i++) {
            assertEquals("M" + i, client.next());
        }
        client.close(1000, "done");
        assertEquals("upper complete", event());
    }

    @Test
    void thePingHandlerReceivesThePingsWhichAreAnswered() throws Exception {
        Client client = connect("/ws/ping");
        client.ws.sendPing(ByteBuffer.wrap("ping-data".getBytes(StandardCharsets.UTF_8))).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals("ping ping-data", client.next());
        assertEquals("ping-data", client.pongs.poll(TIMEOUT, TimeUnit.SECONDS));
        client.close(1000, "done");
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
        return connect(uri(server.getPort(), path), customizer);
    }

    private static Client connect(URI uri, Consumer<WebSocket.Builder> customizer) throws Exception {
        Client client = new Client();
        WebSocket.Builder builder = http.newWebSocketBuilder();
        customizer.accept(builder);
        client.ws = builder.buildAsync(uri, client).get(TIMEOUT, TimeUnit.SECONDS);
        return client;
    }

    private static int handshakeStatus(String path, Consumer<WebSocket.Builder> customizer) throws Exception {
        return handshakeStatus(uri(server.getPort(), path), customizer);
    }

    private static int handshakeStatus(URI uri, Consumer<WebSocket.Builder> customizer) throws Exception {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        customizer.accept(builder);
        ExecutionException error = assertThrows(ExecutionException.class,
            () -> builder.buildAsync(uri, new Client()).get(TIMEOUT, TimeUnit.SECONDS));
        return assertInstanceOf(WebSocketHandshakeException.class, error.getCause()).getResponse().statusCode();
    }

    private static URI uri(int port, String path) {
        return URI.create("ws://localhost:" + port + path);
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
        final BlockingQueue<String> pongs = new LinkedBlockingQueue<>();
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
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            pongs.add(StandardCharsets.UTF_8.decode(message).toString());
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
     * Emits its messages as they are requested, and reports a cancel.
     *
     * @param name     The name of the messages
     * @param count    The number of messages
     * @param complete Whether it completes after them
     * @param log      Reports a cancel
     */
    record Ticks(String name, int count, boolean complete, BlockingQueue<String> log) implements Publisher<String> {

        @Override
        public void subscribe(Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private long requested;
                private int emitted;
                private boolean done;

                @Override
                public synchronized void request(long n) {
                    requested += n;
                    while (requested > 0 && emitted < count && !done) {
                        requested--;
                        emitted++;
                        subscriber.onNext(name + " " + emitted);
                    }
                    if (emitted == count && complete && !done) {
                        done = true;
                        subscriber.onComplete();
                    }
                }

                @Override
                public synchronized void cancel() {
                    if (!done) {
                        done = true;
                        log.add(name + " cancelled");
                    }
                }
            });
        }
    }

    /**
     * Replies to each message in upper case, and requests the next once the reply was sent.
     */
    static final class Upper implements Subscriber<String> {
        private final WebSocketSession session;
        private final BlockingQueue<String> log;
        private Subscription subscription;

        Upper(WebSocketSession session, BlockingQueue<String> log) {
            this.session = session;
            this.log = log;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(String message) {
            session.sendAsync(message.toUpperCase(Locale.ROOT)).thenRun(() -> subscription.request(1));
        }

        @Override
        public void onError(Throwable t) {
            log.add("upper error " + t.getMessage());
        }

        @Override
        public void onComplete() {
            log.add("upper complete");
        }
    }

    /**
     * Replies to each message with the thread that received it, and whether there is a current request.
     */
    static final class Threads implements Subscriber<String> {
        private final WebSocketSession session;

        Threads(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String message) {
            session.sendAsync(Thread.currentThread().getName() + " " + ServerRequestContext.currentRequest().isPresent());
        }

        @Override
        public void onError(Throwable t) {
            // the connection closes
        }

        @Override
        public void onComplete() {
            // the connection closes
        }
    }

    /**
     * Replies to the first message, and cancels.
     */
    static final class First implements Subscriber<String> {
        private final WebSocketSession session;
        private Subscription subscription;

        First(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(String message) {
            subscription.cancel();
            session.sendAsync("first " + message);
        }

        @Override
        public void onError(Throwable t) {
            // it cancelled: no signal follows
        }

        @Override
        public void onComplete() {
            // it cancelled: no signal follows
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
                routes.GET("/ws/chat/{room}").webSocket(ws -> ws
                    .subprotocols("chat.v1", "chat.v2")
                    .onOpen((session, request) -> session.sendAsync("open " + room(session) + " " + request.getPath()
                        + " " + ServerRequestContext.currentRequest().isPresent()))
                    .onMessage(String.class, (message, session) -> session.sendAsync("echo " + room(session) + " " + message
                        + " " + ServerRequestContext.currentRequest().isPresent()))
                    .onClose((reason, session) -> {
                        log.add("close " + room(session) + " " + reason.getCode() + " " + reason.getReason());
                        return null;
                    }));

                routes.GET("/ws/json").webSocket(ws -> ws
                    .onMessage(Argument.of(Msg.class), (message, session) ->
                        session.sendAsync(new Msg(message.text().toUpperCase(Locale.ROOT), message.count() + 1))));

                routes.GET("/ws/binary").webSocket(ws -> ws
                    .onMessage(byte[].class, (bytes, session) -> session.sendAsync(reverse(bytes))));

                routes.GET("/ws/async").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> CompletableFuture
                        .supplyAsync(() -> message.toUpperCase(Locale.ROOT), CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
                        .thenCompose(session::sendAsync)
                        .thenRun(() -> log.add("handled " + message))));

                routes.GET("/ws/failing").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> switch (message) {
                        case "throw" -> throw new IllegalStateException("thrown " + message);
                        case "stage" -> CompletableFuture.failedFuture(new IllegalStateException("failed " + message));
                        default -> session.sendAsync("ok " + message);
                    })
                    .onError((error, session) -> session.sendAsync("error " + error.getMessage())));

                routes.GET("/ws/unhandled").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        throw new IllegalStateException("unhandled " + message);
                    }));

                routes.GET("/ws/failing-open").webSocket(ws -> ws
                    .onOpen((session, request) -> {
                        throw new IllegalStateException("open failed");
                    })
                    .onError((error, session) -> {
                        log.add("open error " + error.getMessage());
                        return null;
                    }));

                routes.GET("/ws/close").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        session.close(new CloseReason(4000, "done " + message));
                        return null;
                    }));

                routes.GET("/ws/small").webSocket(ws -> ws
                    .maxPayloadLength(8)
                    .onMessage(String.class, (message, session) -> session.sendAsync(message)));

                routes.GET("/ws/push").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("pushed")));

                routes.GET("/ws/pong").webSocket(ws -> ws
                    .onPong((pong, session) -> session.sendAsync("pong " + pong.getContent().toString(StandardCharsets.UTF_8))));

                routes.GET("/ws/guarded")
                    .beforeReplacing(request -> request.getHeaders().contains("X-Allow") ? null : HttpResponse.status(HttpStatus.FORBIDDEN)).and()
                    .webSocket(ws -> ws
                        .onOpen((session, request) -> session.sendAsync("allowed")));

                routes.path("/ws/group", group -> {
                    group.beforeReplacing(request -> request.getHeaders().contains("X-Group") ? null : HttpResponse.status(HttpStatus.FORBIDDEN));
                    group.GET("/{id}")
                        .attribute("kind", "grouped")
                        .webSocket(ws -> ws
                            .onOpen((session, request) -> session.sendAsync("group " + session.getUriVariables().get("id", String.class).orElseThrow()
                                + " " + RouteAttributes.getRouteInfo(request).flatMap(route -> route.getAttribute("kind", String.class)).orElse("-"))));
                });

                routes.GET("/ws/where")
                    .where(RequestPredicates.header("X-Variant", "b"))
                    .webSocket(ws -> ws
                        .onOpen((session, request) -> session.sendAsync("where")));

                // the closest route wins over the order, then the lowest order
                routes.GET("/ws/closest/{name}").order(-10).webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("variable " + session.getUriVariables().get("name", String.class).orElseThrow())));
                routes.GET("/ws/closest/fixed").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("literal")));
                routes.GET("/ws/ordered").order(10).webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("higher order")));
                routes.GET("/ws/ordered").order(-10).webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("lower order")));

                routes.GET("/ws/port").port(routePort).webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync("port " + request.getServerAddress().getPort())));

                // streams, without Reactor
                routes.GET("/ws/ticks/{count}").webSocket(ws -> ws
                    .onOpen((session, request) -> {
                        int count = session.getUriVariables().get("count", Integer.class).orElseThrow();
                        return session.sendAllAsync(new Ticks("tick", count, true, log))
                            .thenRun(() -> log.add("sent " + count + " ticks"));
                    }));
                routes.GET("/ws/replies").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> session.sendAllAsync(new Ticks(message, 3, true, log))));
                routes.GET("/ws/endless").webSocket(ws -> ws
                    .onOpen((session, request) -> {
                        // not returned, so that it does not hold the messages back
                        session.sendAllAsync(new Ticks("endless", 1, false, log));
                        return null;
                    })
                    .onMessage(String.class, (message, session) -> session.sendAsync("echo " + message)));
                routes.GET("/ws/upper").webSocket(ws -> ws
                    .onMessages(String.class, (messages, session) -> {
                        messages.subscribe(new Upper(session, log));
                        return null;
                    }));
                routes.GET("/ws/first").webSocket(ws -> ws
                    .onMessages(String.class, (messages, session) -> {
                        messages.subscribe(new First(session));
                        return null;
                    })
                    .onPing((ping, session) -> session.sendAsync("ping " + ping.getContent().toString(StandardCharsets.UTF_8))));
                routes.GET("/ws/messages-failing").webSocket(ws -> ws
                    .onMessages(String.class, (messages, session) -> CompletableFuture.failedFuture(new IllegalStateException("messages failed")))
                    .onError((error, session) -> {
                        log.add("error " + error.getMessage());
                        return null;
                    }));
                routes.GET("/ws/held").webSocket(ws -> ws
                    // returned: it holds the messages back, but not the pings and the close
                    .onOpen((session, request) -> session.sendAllAsync(new Ticks("held", 1, false, log)))
                    .onMessage(String.class, (message, session) -> null));
                routes.GET("/ws/stuck").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        log.add(message);
                        return new CompletableFuture<>();
                    }));
                routes.GET("/ws/close-discards").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        log.add("start " + message);
                        return new CompletableFuture<>();
                    })
                    .onClose((reason, session) -> {
                        log.add("close " + reason.getCode());
                        return null;
                    }));
                for (String executor : List.of("blocking", "event-loop")) {
                    HttpRouteSpec route = routes.GET("/ws/async-open/" + executor);
                    if (executor.equals("blocking")) {
                        route.executeOn(TaskExecutors.BLOCKING);
                    }
                    route.webSocket(ws -> ws
                        .onOpen((session, request) -> CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)))
                        .onMessages(String.class, (messages, session) -> {
                            messages.subscribe(new Threads(session));
                            return session.sendAsync(Thread.currentThread().getName() + " " + ServerRequestContext.currentRequest().isPresent());
                        }));
                }
                routes.GET("/ws/open-first-concurrent").webSocket(ws -> ws
                    .maxConcurrentMessages(2)
                    .onOpen((session, request) -> CompletableFuture.runAsync(() -> log.add("opened"), CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)))
                    .onMessage(String.class, (message, session) -> {
                        log.add("message " + message);
                        return session.sendAsync("got " + message);
                    }));
                routes.GET("/ws/upper-blocking")
                    .executeOn(TaskExecutors.BLOCKING)
                    .webSocket(ws -> ws
                        .maxConcurrentMessages(4)
                        .onMessages(String.class, (messages, session) -> {
                            messages.subscribe(new Upper(session, log));
                            return null;
                        }));
                routes.GET("/ws/ping").webSocket(ws -> ws
                    .onPing((ping, session) -> session.sendAsync("ping " + ping.getContent().toString(StandardCharsets.UTF_8))));

                // one message after the other, or concurrently
                routes.GET("/ws/serial").webSocket(ws -> ws
                    .onMessage(String.class, (message, session) -> {
                        log.add("start " + message);
                        return CompletableFuture.runAsync(() -> log.add("end " + message), CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS))
                            .thenCompose(ignored -> session.sendAsync("done " + message));
                    }));
                routes.GET("/ws/concurrent").webSocket(ws -> ws
                    .maxConcurrentMessages(2)
                    .onMessage(String.class, (message, session) -> {
                        if (message.equals("first")) {
                            CompletableFuture<Object> second = new CompletableFuture<>();
                            session.put("second", second);
                            return second.thenCompose(ignored -> session.sendAsync("first done"));
                        }
                        CompletableFuture<?> second = session.get("second", CompletableFuture.class).orElseThrow();
                        return session.sendAsync("second done").thenRun(() -> second.complete(null));
                    }));
                routes.GET("/ws/open-first").webSocket(ws -> ws
                    .onOpen((session, request) -> CompletableFuture.runAsync(() -> log.add("opened"), CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)))
                    .onMessage(String.class, (message, session) -> {
                        log.add("message " + message);
                        return session.sendAsync("got " + message);
                    }));

                routes.GET("/ws/executor")
                    .executeOn(TaskExecutors.BLOCKING)
                    .webSocket(ws -> ws
                        .onOpen((session, request) -> session.sendAsync("open on " + Thread.currentThread().getName()))
                        .onMessage(String.class, (message, session) -> session.sendAsync(message + " on " + Thread.currentThread().getName())));

                routes.GET("/ws/event-loop").webSocket(ws -> ws
                    .onOpen((session, request) -> session.sendAsync(Thread.currentThread().getName())));

                routes.GET("/ws/broadcast/{room}").webSocket(ws -> ws
                    .onOpen((session, request) -> {
                        long open = session.getOpenSessions().stream().filter(sameRoom(session)).count();
                        return session.sendAsync("open sessions " + open + " " + room(session));
                    })
                    .onMessage(String.class, (message, session) -> broadcaster.get().broadcastAsync(message, sameRoom(session))));
            };
        }
    }
}
