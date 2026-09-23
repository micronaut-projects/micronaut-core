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
package io.micronaut.websocket.route;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.websocket.CloseReason;
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

    private static final Argument<WebSocketSession> SESSION = Argument.of(WebSocketSession.class, "session");
    @SuppressWarnings("rawtypes")
    private static final Argument<HttpRequest> REQUEST = Argument.of(HttpRequest.class, "request");
    private static final Argument<CloseReason> REASON = Argument.of(CloseReason.class, "reason");
    private static final Argument<Throwable> ERROR = Argument.of(Throwable.class, "error");
    private static final Argument<WebSocketPongMessage> PONG = Argument.of(WebSocketPongMessage.class, "pong");

    private final String uri;
    private @Nullable WebSocketRouteMethod onOpen;
    private @Nullable WebSocketRouteMethod onMessage;
    private @Nullable Argument<?> messageArgument;
    private @Nullable WebSocketRouteMethod onPong;
    private @Nullable WebSocketRouteMethod onClose;
    private @Nullable WebSocketRouteMethod onError;
    private @Nullable String subprotocols;

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

    private static AnnotationMetadata metadata(Map<String, Map<CharSequence, Object>> annotations) {
        return new DefaultAnnotationMetadata(annotations, Map.of(), Map.of(), annotations, Map.of(), false);
    }

    /**
     * Declares the handlers of the endpoint.
     */
    final class Spec implements WebSocketRouteSpec {

        private boolean closed;
        private @Nullable WebSocketOpenHandler openHandler;
        private @Nullable Argument<?> messageType;
        private @Nullable WebSocketMessageHandler<?> messageHandler;
        private @Nullable WebSocketMessageHandler<WebSocketPongMessage> pongHandler;
        private @Nullable WebSocketCloseHandler closeHandler;
        private @Nullable WebSocketErrorHandler errorHandler;
        private @Nullable List<String> protocols;
        private int maxPayloadLength = DEFAULT_MAX_PAYLOAD_LENGTH;

        @Override
        public WebSocketRouteSpec onOpen(WebSocketOpenHandler handler) {
            checkOpen("onOpen", openHandler);
            openHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        @Override
        public <T> WebSocketRouteSpec onMessage(Argument<T> messageType, WebSocketMessageHandler<T> handler) {
            checkOpen("onMessage", messageHandler);
            this.messageType = Objects.requireNonNull(messageType, "messageType");
            messageHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        @Override
        public WebSocketRouteSpec onPong(WebSocketMessageHandler<WebSocketPongMessage> handler) {
            checkOpen("onPong", pongHandler);
            pongHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        @Override
        public WebSocketRouteSpec onClose(WebSocketCloseHandler handler) {
            checkOpen("onClose", closeHandler);
            closeHandler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        @Override
        public WebSocketRouteSpec onError(WebSocketErrorHandler handler) {
            checkOpen("onError", errorHandler);
            errorHandler = Objects.requireNonNull(handler, "handler");
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
            checkOpen("maxPayloadLength", null);
            if (maxPayloadLength <= 0) {
                throw new IllegalArgumentException("The maximum payload length of the WebSocket route " + uri + " must be positive: " + maxPayloadLength);
            }
            this.maxPayloadLength = maxPayloadLength;
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

        @SuppressWarnings({"unchecked", "rawtypes"})
        void build() {
            WebSocketRouteEndpoint endpoint = WebSocketRouteEndpoint.this;
            WebSocketOpenHandler open = openHandler;
            if (open != null) {
                onOpen = new WebSocketRouteMethod(endpoint, "onOpen", AnnotationMetadata.EMPTY_METADATA,
                    args -> open.onOpen((WebSocketSession) args[0], (HttpRequest<?>) args[1]),
                    SESSION, REQUEST);
            }
            WebSocketMessageHandler message = messageHandler;
            Argument<?> type = messageType;
            if (message != null && type != null) {
                Argument<?> argument = Argument.of(type.getType(), "message", type.getAnnotationMetadata(), type.getTypeParameters());
                messageArgument = argument;
                onMessage = new WebSocketRouteMethod(endpoint, "onMessage",
                    metadata(Map.of(OnMessage.class.getName(), Map.<CharSequence, Object>of(MAX_PAYLOAD_LENGTH, maxPayloadLength))),
                    args -> message.onMessage(args[0], (WebSocketSession) args[1]),
                    argument, SESSION);
            }
            WebSocketMessageHandler<WebSocketPongMessage> pong = pongHandler;
            if (pong != null) {
                onPong = new WebSocketRouteMethod(endpoint, "onPong", metadata(Map.of(OnMessage.class.getName(), Map.of())),
                    args -> pong.onMessage((WebSocketPongMessage) args[0], (WebSocketSession) args[1]),
                    PONG, SESSION);
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
        }
    }
}
