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
package io.micronaut.http.server.tck;

/**
 * The JUnit tags of the WebSocket tests of the server TCK. Every WebSocket test carries
 * {@link #WEBSOCKET}; a test of an operation that a server implementation might not support
 * carries the tag of that operation too, so that a runner can exclude just that operation, e.g.
 * {@code @ExcludeTags(WebSocketTckTags.WEBSOCKET_SUBPROTOCOL)}, or every WebSocket test with
 * {@code @ExcludeTags(WebSocketTckTags.WEBSOCKET)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
public final class WebSocketTckTags {

    /**
     * Every WebSocket test. Excluding it skips all of them, for a server that cannot upgrade a
     * connection to WebSocket at all, e.g. a servlet container or a function runtime.
     */
    public static final String WEBSOCKET = "websocket";

    /**
     * Binary messages. Excluding it skips the tests that send and receive binary frames.
     */
    public static final String WEBSOCKET_BINARY = "websocket-binary";

    /**
     * A text message the client sends in fragments. Excluding it skips the tests that check that
     * the fragments are one message.
     */
    public static final String WEBSOCKET_FRAGMENTATION = "websocket-fragmentation";

    /**
     * The subprotocol negotiation of the handshake. Excluding it skips the tests that offer
     * subprotocols and check the one the server selects.
     */
    public static final String WEBSOCKET_SUBPROTOCOL = "websocket-subprotocol";

    /**
     * The close handshake: the server answers the close frame of the client with its own, and a
     * handler closes the connection with a code and a reason. Excluding it skips those tests.
     */
    public static final String WEBSOCKET_CLOSE_HANDSHAKE = "websocket-close-handshake";

    /**
     * Ping and pong frames: the server answers a ping, and the ping and pong handlers of a route
     * receive them. Excluding it skips those tests.
     */
    public static final String WEBSOCKET_PING_PONG = "websocket-ping-pong";

    /**
     * A route or endpoint handler receiving the pings of the client. A server whose WebSocket
     * container answers pings itself, and never hands them to the endpoint, cannot do this.
     * Excluding it skips those tests and keeps the other ping and pong tests.
     */
    public static final String WEBSOCKET_PING_HANDLER = "websocket-ping-handler";

    /**
     * The maximum payload length of a message: a larger message closes the connection with
     * 1009. Excluding it skips those tests.
     */
    public static final String WEBSOCKET_MAX_PAYLOAD = "websocket-max-payload";

    /**
     * The {@link io.micronaut.websocket.WebSocketBroadcaster} and the open sessions. Excluding it
     * skips the tests that broadcast to the sessions of other connections.
     */
    public static final String WEBSOCKET_BROADCAST = "websocket-broadcast";

    /**
     * WebSocket routes declared with the route builder ({@code HttpRoutes}) rather than
     * {@code @ServerWebSocket} beans. Excluding it skips the tests of those routes.
     */
    public static final String WEBSOCKET_FUNCTIONAL_ROUTES = "websocket-functional-routes";

    /**
     * Handlers that return a {@link java.util.concurrent.CompletionStage} or a
     * {@link org.reactivestreams.Publisher}. Excluding it skips the tests of those handlers.
     */
    public static final String WEBSOCKET_ASYNC_HANDLERS = "websocket-async-handlers";

    /**
     * Filters of the upgrade request: a filter that answers 403 rejects the handshake.
     * Excluding it skips those tests.
     */
    public static final String WEBSOCKET_UPGRADE_FILTER = "websocket-upgrade-filter";

    /**
     * The upgrade request in the handlers: an {@link io.micronaut.http.HttpRequest} argument, its
     * headers, and the current request of {@code ServerRequestContext}. Excluding it skips those
     * tests.
     */
    public static final String WEBSOCKET_REQUEST_SCOPE = "websocket-request-scope";

    /**
     * The status of an upgrade request that the server refuses: no route (404) or not an upgrade
     * (400). Excluding it skips those tests.
     */
    public static final String WEBSOCKET_HANDSHAKE_ERRORS = "websocket-handshake-errors";

    /**
     * The error handlers: a failing handler reaches {@code @OnError} or the error handler of the
     * route, or closes the connection with 1011. Excluding it skips those tests.
     */
    public static final String WEBSOCKET_ERROR_HANDLING = "websocket-error-handling";

    private WebSocketTckTags() {
    }
}
