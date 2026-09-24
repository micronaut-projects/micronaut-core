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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.websocket.WebSocketPongMessage;

/**
 * The handlers of a WebSocket endpoint declared as a route of handler functions, the functional
 * counterpart of a {@link io.micronaut.websocket.annotation.ServerWebSocket} bean. The route
 * builder of the server declares it:
 *
 * <pre>{@code
 * routes.webSocket("/chat/{room}", ws -> ws
 *     .subprotocols("chat.v1")
 *     .onOpen((session, request) -> session.sendAsync("joined"))
 *     .onMessage(Argument.of(ChatMessage.class), (message, session) -> broadcaster.broadcastAsync(message, sameRoom(session)))
 *     .onClose((reason, session) -> null)
 *     .onError((error, session) -> null));
 * }</pre>
 *
 * <p>The handlers run like the methods of a {@code @ServerWebSocket} bean: the path variables of
 * the route are the {@link io.micronaut.websocket.WebSocketSession#getUriVariables() URI variables}
 * of the session, the session is one of the open sessions the
 * {@link io.micronaut.websocket.WebSocketBroadcaster} reaches, the request of the upgrade is the
 * current request, and a handler that returns a stage is done when the stage completes. The
 * filters, conditions and attributes of the route apply to the upgrade request, and its executor,
 * see {@code HttpRouteSpec#executeOn(String)}, runs the handlers. The handlers are shared by all
 * the connections of the route: state of a connection belongs in the
 * {@link io.micronaut.websocket.WebSocketSession#getAttributes() attributes of its session}.</p>
 *
 * <p>The broadcaster depends on the server, which depends on the routes: a bean that declares
 * routes reaches it through a {@link io.micronaut.context.BeanProvider}, e.g.
 * {@code BeanProvider<WebSocketBroadcaster>}.</p>
 *
 * <p>The messages are delivered as they arrive: the handler of a message may still run when the
 * next message arrives.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface WebSocketRouteSpec permits WebSocketRouteEndpoint.Spec {

    /**
     * Handle the opening of a connection.
     *
     * @param handler The handler
     * @return This spec
     */
    WebSocketRouteSpec onOpen(WebSocketOpenHandler handler);

    /**
     * Handle the messages of a connection, decoded to a type like the message parameter of an
     * {@link io.micronaut.websocket.annotation.OnMessage} method: {@code String} and
     * {@code byte[]} as they are, other types from JSON. A connection to a route
     * without a message handler is closed with
     * {@link io.micronaut.websocket.CloseReason#UNSUPPORTED_DATA} when a message arrives.
     *
     * @param messageType The type of the messages
     * @param handler     The handler
     * @param <T>         The type of the messages
     * @return This spec
     */
    <T> WebSocketRouteSpec onMessage(Argument<T> messageType, WebSocketMessageHandler<T> handler);

    /**
     * Handle the messages of a connection, see {@link #onMessage(Argument, WebSocketMessageHandler)}.
     *
     * @param messageType The type of the messages
     * @param handler     The handler
     * @param <T>         The type of the messages
     * @return This spec
     */
    default <T> WebSocketRouteSpec onMessage(Class<T> messageType, WebSocketMessageHandler<T> handler) {
        return onMessage(Argument.of(messageType), handler);
    }

    /**
     * Handle the pong messages of a connection.
     *
     * @param handler The handler
     * @return This spec
     */
    WebSocketRouteSpec onPong(WebSocketMessageHandler<WebSocketPongMessage> handler);

    /**
     * Handle the closing of a connection.
     *
     * @param handler The handler
     * @return This spec
     */
    WebSocketRouteSpec onClose(WebSocketCloseHandler handler);

    /**
     * Handle the errors of a connection: a handler that fails, a message that cannot be decoded.
     *
     * @param handler The handler
     * @return This spec
     */
    WebSocketRouteSpec onError(WebSocketErrorHandler handler);

    /**
     * The subprotocols the endpoint supports, like
     * {@link io.micronaut.websocket.annotation.ServerWebSocket#subprotocols()}: the handshake
     * selects the first one the client asks for, see
     * {@link io.micronaut.websocket.WebSocketSession#getSubprotocol()}.
     *
     * @param subprotocols The subprotocols
     * @return This spec
     */
    WebSocketRouteSpec subprotocols(String... subprotocols);

    /**
     * The maximum size of a message, like
     * {@link io.micronaut.websocket.annotation.OnMessage#maxPayloadLength()}: {@code 65536} bytes
     * by default. A larger message closes the connection with
     * {@link io.micronaut.websocket.CloseReason#MESSAGE_TO_BIG}.
     *
     * @param maxPayloadLength The maximum size in bytes
     * @return This spec
     */
    WebSocketRouteSpec maxPayloadLength(int maxPayloadLength);
}
