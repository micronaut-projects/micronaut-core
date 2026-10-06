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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.websocket.WebSocketPingMessage;
import io.micronaut.websocket.WebSocketPongMessage;

/**
 * The handlers of a WebSocket endpoint declared as a route of handler functions, the functional
 * counterpart of a {@link io.micronaut.websocket.annotation.ServerWebSocket} bean. The route
 * builder of the server declares it, as the terminal of a {@code GET} route:
 *
 * <pre>{@code
 * routes.GET("/chat/{room}").webSocket(ws -> ws
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
 * <p>The handlers of a connection run one after the other: the first message is handled once the
 * open handler is done, and each next message once the handler of the previous one is done.
 * {@link #maxConcurrentMessages(int)} lets the handlers of more messages of a connection run at
 * the same time. The connection reads only a few messages ahead of its handlers, so a client
 * cannot send messages faster than they are handled, and it answers the pings meanwhile. A close
 * is handled once the messages before it were handled, but does not wait for the open handler. A
 * handler that returns a stage that does not complete, e.g. of a stream that does not end, holds
 * the next messages back: a handler that streams while the connection receives starts the stream
 * and returns {@code null}.</p>
 *
 * <p>Streams: {@link io.micronaut.websocket.WebSocketSession#sendAllAsync(org.reactivestreams.Publisher)}
 * sends the messages of a publisher, at the pace the connection writes them, e.g. from the open
 * handler, or as the replies to a message; {@link #onMessages(Argument, WebSocketMessagesHandler)}
 * receives the messages of a connection as a publisher, at the pace its subscriber requests them.</p>
 *
 * <pre>{@code
 * routes.GET("/ticks").webSocket(ws -> ws
 *     .onOpen((session, request) -> session.sendAllAsync(ticks)));
 *
 * routes.GET("/upper").webSocket(ws -> ws
 *     .onMessages(String.class, (messages, session) -> session.sendAllAsync(Flux.from(messages).map(String::toUpperCase))));
 * }</pre>
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
     * {@link io.micronaut.websocket.CloseReason#UNSUPPORTED_DATA} when a message arrives. A route
     * has a message handler or a {@link #onMessages(Argument, WebSocketMessagesHandler) messages handler}.
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
     * Handle the messages of a connection as a stream: the handler is called once for each
     * connection, after the open handler, with a publisher of the messages of the connection,
     * decoded like for {@link #onMessage(Argument, WebSocketMessageHandler)}, in the order they are
     * read. A message is handled once the subscriber requested it, so a subscriber that requests
     * no more holds the next messages back, and one that cancels discards the messages that
     * follow. The publisher has a single subscriber, which the handler subscribes before it is
     * done: the messages of a handler that is done without a subscriber, or that fails, are
     * discarded. The publisher completes when the connection closes, once the subscriber received
     * the messages read before. The subscriber is signaled on the executor of the route. A route
     * has a messages handler or a message handler.
     *
     * <pre>{@code
     * ws.onMessages(String.class, (messages, session) -> session.sendAllAsync(Flux.from(messages).map(String::toUpperCase)));
     * }</pre>
     *
     * @param messageType The type of the messages
     * @param handler     The handler
     * @param <T>         The type of the messages
     * @return This spec
     */
    <T> WebSocketRouteSpec onMessages(Argument<T> messageType, WebSocketMessagesHandler<T> handler);

    /**
     * Handle the messages of a connection as a stream, see {@link #onMessages(Argument, WebSocketMessagesHandler)}.
     *
     * @param messageType The type of the messages
     * @param handler     The handler
     * @param <T>         The type of the messages
     * @return This spec
     */
    default <T> WebSocketRouteSpec onMessages(Class<T> messageType, WebSocketMessagesHandler<T> handler) {
        return onMessages(Argument.of(messageType), handler);
    }

    /**
     * Handle the ping messages of a connection: the server answers each ping with a pong itself,
     * and then calls the handler.
     *
     * @param handler The handler
     * @return This spec
     */
    WebSocketRouteSpec onPing(WebSocketMessageHandler<WebSocketPingMessage> handler);

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

    /**
     * The most messages of a connection that are handled at the same time: {@code 1} by default,
     * the handlers of the messages of a connection run one after the other, in order. With more,
     * they may run concurrently and complete in any order. The next messages wait while that many
     * are handled. For a {@link #onMessages(Argument, WebSocketMessagesHandler) messages handler},
     * it is the most messages offered to the stream before the subscriber requests them.
     *
     * @param maxConcurrentMessages The most messages handled at the same time
     * @return This spec
     */
    WebSocketRouteSpec maxConcurrentMessages(int maxConcurrentMessages);
}
