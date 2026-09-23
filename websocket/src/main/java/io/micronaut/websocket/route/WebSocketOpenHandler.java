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
import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * Called when a connection to a WebSocket route opens, like an
 * {@link io.micronaut.websocket.annotation.OnOpen} method.
 *
 * <pre>{@code
 * ws.onOpen((session, request) -> session.sendAsync("joined " + session.getUriVariables().get("room", String.class).orElseThrow()));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see WebSocketRouteSpec#onOpen(WebSocketOpenHandler)
 */
@Experimental
@FunctionalInterface
public interface WebSocketOpenHandler {

    /**
     * The connection opened.
     *
     * @param session The session of the connection
     * @param request The upgrade request
     * @return A stage that completes when the handler is done, or {@code null} if it is done
     * @throws Exception An error, which closes the connection and is passed to the
     *                   {@link WebSocketRouteSpec#onError(WebSocketErrorHandler) error handler}
     */
    @Nullable CompletionStage<?> onOpen(WebSocketSession session, HttpRequest<?> request) throws Exception;
}
