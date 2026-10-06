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
package io.micronaut.web.router.websocket;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPingMessage;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.context.WebSocketBean;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The endpoint of a WebSocket route: a {@link WebSocketBean} whose methods are the handler
 * functions a {@link WebSocketRouteSpec} declared. The route builder adds a {@code GET} route
 * that carries it as the attribute {@link #ROUTE_ATTRIBUTE} and has the annotations
 * {@link #ROUTE_METADATA}, which make it a WebSocket route: the server upgrades a request to it
 * and hands the connection to this endpoint instead of to a bean looked up by type.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class WebSocketRouteEndpoint implements WebSocketBean<Object> {

    /**
     * The route attribute that holds the endpoint of a WebSocket route.
     */
    public static final String ROUTE_ATTRIBUTE = "micronaut.websocket.route.endpoint";

    /**
     * The annotations of a WebSocket route: {@code @OnMessage} and {@code @OnOpen}, which the server
     * looks for to find the WebSocket routes, and to answer a plain request to one with an error.
     */
    public static final AnnotationMetadata ROUTE_METADATA = metadata(Map.of(
        OnMessage.class.getName(), Map.of(),
        OnOpen.class.getName(), Map.of()
    ));

    private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 65536;
    private static final String MAX_PAYLOAD_LENGTH = "maxPayloadLength";
    private static final String HANDLER = "handler";
    private static final String ON_OPEN = "onOpen";
    private static final String ON_MESSAGE = "onMessage";
    private static final String ON_MESSAGES = "onMessages";

    private static final Argument<WebSocketSession> SESSION = Argument.of(WebSocketSession.class, "session");
    @SuppressWarnings("rawtypes")
    private static final Argument<HttpRequest> REQUEST = Argument.of(HttpRequest.class, "request");
    private static final Argument<CloseReason> REASON = Argument.of(CloseReason.class, "reason");
    private static final Argument<Throwable> ERROR = Argument.of(Throwable.class, "error");
    private static final Argument<WebSocketPongMessage> PONG = Argument.of(WebSocketPongMessage.class, "pong");
    private static final Argument<WebSocketPingMessage> PING = Argument.of(WebSocketPingMessage.class, "ping");

    private final String uri;
    private @Nullable WebSocketRouteMethod onOpen;
    private @Nullable WebSocketRouteMethod onMessage;
    private @Nullable Argument<?> messageArgument;
    private @Nullable WebSocketRouteMethod onPong;
    private @Nullable WebSocketRouteMethod onPing;
    private @Nullable WebSocketRouteMethod onClose;
    private @Nullable WebSocketRouteMethod onError;
    private @Nullable String subprotocols;
    private int maxConcurrentMessages = 1;
    /**
     * Whether the endpoint has a {@link WebSocketMessagesHandler}, which receives the messages of
     * each connection as a stream, see {@link #connected(WebSocketSession, Consumer)}.
     */
    private boolean streaming;
    /**
     * The open connections of an endpoint with a {@link WebSocketMessagesHandler}.
     */
    private final Map<WebSocketSession, Connection> connections = new ConcurrentHashMap<>();

    private WebSocketRouteEndpoint(String uri) {
        this.uri = uri;
    }

    /**
     * The endpoint of a WebSocket route.
     *
     * @param uri         The URI template of the route, for the messages
     * @param declaration Declares the handlers of the endpoint
     * @return The endpoint
     */
    public static WebSocketRouteEndpoint of(String uri, Consumer<? super WebSocketRouteSpec> declaration) {
        Objects.requireNonNull(declaration, "declaration");
        WebSocketRouteEndpoint endpoint = new WebSocketRouteEndpoint(Objects.requireNonNull(uri, "uri"));
        Spec spec = endpoint.new Spec();
        try {
            declaration.accept(spec);
        } finally {
            // the handlers of the endpoint are the ones declared in the lambda
            spec.closed = true;
        }
        spec.build();
        return endpoint;
    }

    /**
     * The message parameter of the message handler, which is never bound from the upgrade request.
     *
     * @return The message parameter, or {@code null} without a message handler
     */
    public @Nullable Argument<?> messageArgument() {
        return messageArgument;
    }

    /**
     * The message parameter of the pong handler, which is never bound from the upgrade request.
     *
     * @return The message parameter, or {@code null} without a pong handler
     */
    public @Nullable Argument<?> pongArgument() {
        return onPong == null ? null : PONG;
    }

    /**
     * The handler of the pings, which a {@link WebSocketBean} does not have.
     *
     * @return The ping handler, or {@code null}
     */
    public @Nullable MethodExecutionHandle<Object, ?> pingMethod() {
        return onPing;
    }

    /**
     * The message parameter of the ping handler.
     *
     * @return The message parameter, or {@code null} without a ping handler
     */
    public @Nullable Argument<?> pingArgument() {
        return onPing == null ? null : PING;
    }

    /**
     * @return The most messages of a connection that are handled at the same time, see
     * {@link WebSocketRouteSpec#maxConcurrentMessages(int)}
     */
    public int maxConcurrentMessages() {
        return maxConcurrentMessages;
    }

    /**
     * @return Whether the endpoint receives the messages of a connection as a stream, see
     * {@link WebSocketRouteSpec#onMessages(Argument, WebSocketMessagesHandler)}: its message
     * method only offers them to the stream, in the order they are read
     */
    public boolean streamsMessages() {
        return streaming;
    }

    /**
     * A connection opened, before its open handler is called.
     *
     * @param session  The session of the connection
     * @param executor Runs the handlers of the route like the server does, on its executor, with
     *                 the upgrade request as the current request: the messages handler once an
     *                 asynchronous open handler is done, and the signals to the subscriber of the
     *                 stream of the messages
     * @param errors   Handles an error of the connection outside of its handlers, e.g. of the
     *                 stage of its {@link WebSocketMessagesHandler}, like the error of a handler
     */
    public void connected(WebSocketSession session, Executor executor, Consumer<Throwable> errors) {
        if (streaming) {
            connections.put(session, new Connection(new WebSocketMessageStream<>(executor, errors), executor, errors));
        }
    }

    /**
     * A connection closed: the stream of its messages completes.
     *
     * @param session The session of the connection
     */
    public void disconnected(WebSocketSession session) {
        Connection connection = streaming ? connections.remove(session) : null;
        if (connection != null) {
            connection.messages.complete();
        }
    }

    @Override
    public BeanDefinition<Object> getBeanDefinition() {
        throw new UnsupportedOperationException("The WebSocket route " + uri + " has handler functions, not a bean");
    }

    @Override
    public Object getTarget() {
        return this;
    }

    @Override
    public Optional<String> getSubprotocols() {
        return Optional.ofNullable(subprotocols);
    }

    @Override
    public Optional<MethodExecutionHandle<Object, ?>> messageMethod() {
        return Optional.ofNullable(onMessage);
    }

    @Override
    public Optional<MethodExecutionHandle<Object, ?>> pongMethod() {
        return Optional.ofNullable(onPong);
    }

    @Override
    public Optional<MethodExecutionHandle<Object, ?>> closeMethod() {
        return Optional.ofNullable(onClose);
    }

    @Override
    public Optional<MethodExecutionHandle<Object, ?>> openMethod() {
        return Optional.ofNullable(onOpen);
    }

    @Override
    public Optional<MethodExecutionHandle<Object, ?>> errorMethod() {
        return Optional.ofNullable(onError);
    }

    @Override
    public String toString() {
        return "WebSocket route " + uri;
    }

    /**
     * The open handler of an endpoint with a {@link WebSocketMessagesHandler}: once the open
     * handler of the route is done, the messages handler starts on the stream of the connection.
     */
    @SuppressWarnings("unchecked")
    private @Nullable CompletionStage<?> open(@Nullable WebSocketOpenHandler open, WebSocketMessagesHandler<?> handler,
                                              WebSocketSession session, HttpRequest<?> request) throws Exception {
        CompletionStage<?> opened = open == null ? null : open.onOpen(session, request);
        if (opened == null) {
            startMessages((WebSocketMessagesHandler<Object>) handler, session);
            return null;
        }
        Connection connection = connections.get(session);
        if (connection == null) {
            // closed before it opened
            return opened;
        }
        // like a handler: on the executor of the route, not on the thread that completed the stage
        return opened.thenRunAsync(() -> startMessages((WebSocketMessagesHandler<Object>) handler, session), connection.executor());
    }

    private void startMessages(WebSocketMessagesHandler<Object> handler, WebSocketSession session) {
        Connection connection = connections.get(session);
        if (connection == null) {
            // closed before it opened
            return;
        }
        CompletionStage<?> handled;
        try {
            handled = handler.onMessages(connection.messages, session);
        } catch (Exception e) {
            // like a method: the open handler fails, and no one receives the messages
            connection.messages.discard();
            ExceptionUtils.sneakyThrow(e);
            return;
        }
        if (handled == null) {
            discardIfUnsubscribed(connection);
            return;
        }
        handled.whenComplete((ignored, error) -> {
            if (error == null) {
                discardIfUnsubscribed(connection);
            } else {
                // the handler failed: its messages go nowhere
                connection.messages.discard();
                connection.errors.accept(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            }
        });
    }

    /**
     * A handler that is done without a subscriber does not receive the messages: they are
     * discarded, so that the connection handles the next.
     */
    private static void discardIfUnsubscribed(Connection connection) {
        if (!connection.messages.hasSubscriber()) {
            connection.messages.discard();
        }
    }

    /**
     * The message handler of an endpoint with a {@link WebSocketMessagesHandler}: it is done when
     * the subscriber of the stream received the message, which is when the connection reads on.
     */
    private CompletionStage<?> offer(Object message, WebSocketSession session) {
        Connection connection = connections.get(session);
        return connection == null ? WebSocketRouteMethod.DONE : connection.messages.offer(message);
    }

    private static AnnotationMetadata metadata(Map<String, Map<CharSequence, Object>> annotations) {
        return new DefaultAnnotationMetadata(annotations, Map.of(), Map.of(), annotations, Map.of(), false);
    }

    /**
     * An open connection of an endpoint with a {@link WebSocketMessagesHandler}.
     *
     * @param messages The stream of its messages
     * @param executor Runs its handlers
     * @param errors   Handles an error of the connection outside of its handlers
     */
    private record Connection(WebSocketMessageStream<Object> messages, Executor executor, Consumer<Throwable> errors) {
    }

    /**
     * Declares the handlers of the endpoint.
     */
    final class Spec implements WebSocketRouteSpec {

        private boolean closed;
        private @Nullable WebSocketOpenHandler openHandler;
        private @Nullable Argument<?> messageType;
        private @Nullable WebSocketMessageHandler<?> messageHandler;
        private @Nullable WebSocketMessagesHandler<?> messagesHandler;
        private @Nullable WebSocketMessageHandler<WebSocketPongMessage> pongHandler;
        private @Nullable WebSocketMessageHandler<WebSocketPingMessage> pingHandler;
        private @Nullable WebSocketCloseHandler closeHandler;
        private @Nullable WebSocketErrorHandler errorHandler;
        private @Nullable List<String> protocols;
        private int maxPayloadLength = DEFAULT_MAX_PAYLOAD_LENGTH;
        private int maxConcurrent = 1;

        @Override
        public WebSocketRouteSpec onOpen(WebSocketOpenHandler handler) {
            checkOpen(ON_OPEN, openHandler);
            openHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public <T> WebSocketRouteSpec onMessage(Argument<T> messageType, WebSocketMessageHandler<T> handler) {
            checkMessages(ON_MESSAGE);
            this.messageType = Objects.requireNonNull(messageType, "messageType");
            messageHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public <T> WebSocketRouteSpec onMessages(Argument<T> messageType, WebSocketMessagesHandler<T> handler) {
            checkMessages(ON_MESSAGES);
            this.messageType = Objects.requireNonNull(messageType, "messageType");
            messagesHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public WebSocketRouteSpec onPing(WebSocketMessageHandler<WebSocketPingMessage> handler) {
            checkOpen("onPing", pingHandler);
            pingHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public WebSocketRouteSpec onPong(WebSocketMessageHandler<WebSocketPongMessage> handler) {
            checkOpen("onPong", pongHandler);
            pongHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public WebSocketRouteSpec onClose(WebSocketCloseHandler handler) {
            checkOpen("onClose", closeHandler);
            closeHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public WebSocketRouteSpec onError(WebSocketErrorHandler handler) {
            checkOpen("onError", errorHandler);
            errorHandler = Objects.requireNonNull(handler, HANDLER);
            return this;
        }

        @Override
        public WebSocketRouteSpec subprotocols(String... subprotocols) {
            checkOpen("subprotocols", protocols);
            Objects.requireNonNull(subprotocols, "subprotocols");
            for (String subprotocol : subprotocols) {
                if (subprotocol == null || subprotocol.isBlank() || subprotocol.indexOf(',') >= 0) {
                    throw new IllegalArgumentException("Invalid subprotocol of the WebSocket route " + uri + ": " + subprotocol);
                }
            }
            protocols = List.of(subprotocols);
            return this;
        }

        @Override
        public WebSocketRouteSpec maxPayloadLength(int maxPayloadLength) {
            checkOpen(MAX_PAYLOAD_LENGTH, null);
            if (maxPayloadLength <= 0) {
                throw new IllegalArgumentException("The maximum payload length of the WebSocket route " + uri + " must be positive: " + maxPayloadLength);
            }
            this.maxPayloadLength = maxPayloadLength;
            return this;
        }

        @Override
        public WebSocketRouteSpec maxConcurrentMessages(int maxConcurrentMessages) {
            checkOpen("maxConcurrentMessages", null);
            if (maxConcurrentMessages <= 0) {
                throw new IllegalArgumentException("The most concurrent messages of the WebSocket route " + uri + " must be positive: " + maxConcurrentMessages);
            }
            this.maxConcurrent = maxConcurrentMessages;
            return this;
        }

        private void checkOpen(String what, @Nullable Object current) {
            if (closed) {
                throw new IllegalStateException("The WebSocket route " + uri + " is declared: declare its handlers in its lambda");
            }
            if (current != null) {
                throw new IllegalStateException("The WebSocket route " + uri + " already has " + what);
            }
        }

        /**
         * A route has one message handler or one messages handler.
         */
        private void checkMessages(String what) {
            checkOpen(what, null);
            if (messageHandler != null || messagesHandler != null) {
                throw new IllegalStateException("The WebSocket route " + uri + " already has "
                    + (messageHandler != null ? ON_MESSAGE : ON_MESSAGES) + ", it cannot have " + what + " too");
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        void build() {
            WebSocketRouteEndpoint endpoint = WebSocketRouteEndpoint.this;
            WebSocketOpenHandler open = openHandler;
            if (open != null) {
                onOpen = new WebSocketRouteMethod(endpoint, ON_OPEN, AnnotationMetadata.EMPTY_METADATA,
                    args -> open.onOpen((WebSocketSession) args[0], (HttpRequest<?>) args[1]),
                    SESSION, REQUEST);
            }
            Argument<?> type = messageType;
            Argument<?> argument = type == null ? null : Argument.of(type.getType(), "message", type.getAnnotationMetadata(), type.getTypeParameters());
            AnnotationMetadata messageMetadata = metadata(Map.of(OnMessage.class.getName(), Map.<CharSequence, Object>of(MAX_PAYLOAD_LENGTH, maxPayloadLength)));
            WebSocketMessageHandler message = messageHandler;
            WebSocketMessagesHandler<?> messages = messagesHandler;
            if (message != null && argument != null) {
                messageArgument = argument;
                onMessage = new WebSocketRouteMethod(endpoint, ON_MESSAGE, messageMetadata,
                    args -> message.onMessage(args[0], (WebSocketSession) args[1]),
                    argument, SESSION);
            } else if (messages != null && argument != null) {
                messageArgument = argument;
                streaming = true;
                // a message is done once the subscriber of the stream of the connection received it
                onMessage = new WebSocketRouteMethod(endpoint, ON_MESSAGES, messageMetadata,
                    args -> endpoint.offer(args[0], (WebSocketSession) args[1]),
                    argument, SESSION);
                // the messages handler starts once the open handler is done
                onOpen = new WebSocketRouteMethod(endpoint, ON_OPEN, AnnotationMetadata.EMPTY_METADATA,
                    args -> endpoint.open(open, messages, (WebSocketSession) args[0], (HttpRequest<?>) args[1]),
                    SESSION, REQUEST);
            }
            WebSocketMessageHandler<WebSocketPongMessage> pong = pongHandler;
            if (pong != null) {
                onPong = new WebSocketRouteMethod(endpoint, "onPong", metadata(Map.of(OnMessage.class.getName(), Map.of())),
                    args -> pong.onMessage((WebSocketPongMessage) args[0], (WebSocketSession) args[1]),
                    PONG, SESSION);
            }
            WebSocketMessageHandler<WebSocketPingMessage> ping = pingHandler;
            if (ping != null) {
                onPing = new WebSocketRouteMethod(endpoint, "onPing", AnnotationMetadata.EMPTY_METADATA,
                    args -> ping.onMessage((WebSocketPingMessage) args[0], (WebSocketSession) args[1]),
                    PING, SESSION);
            }
            WebSocketCloseHandler close = closeHandler;
            if (close != null) {
                onClose = new WebSocketRouteMethod(endpoint, "onClose", AnnotationMetadata.EMPTY_METADATA,
                    args -> close.onClose((CloseReason) args[0], (WebSocketSession) args[1]),
                    REASON, SESSION);
            }
            WebSocketErrorHandler error = errorHandler;
            if (error != null) {
                onError = new WebSocketRouteMethod(endpoint, "onError", AnnotationMetadata.EMPTY_METADATA,
                    args -> error.onError((Throwable) args[0], (WebSocketSession) args[1]),
                    ERROR, SESSION);
            }
            List<String> supported = protocols;
            if (supported != null && !supported.isEmpty()) {
                subprotocols = String.join(",", supported);
            }
            maxConcurrentMessages = maxConcurrent;
        }
    }
}
