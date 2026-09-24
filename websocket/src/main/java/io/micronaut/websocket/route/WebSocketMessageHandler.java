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
import io.micronaut.websocket.WebSocketSession;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * Called for each message a connection to a WebSocket route receives, like an
 * {@link io.micronaut.websocket.annotation.OnMessage} method. The message is decoded to the type
 * the handler was registered for, like the message parameter of an {@code @OnMessage} method:
 * {@code String} and {@code byte[]} as they are, other types from JSON.
 *
 * <pre>{@code
 * ws.onMessage(Argument.of(ChatMessage.class), (message, session) -> session.sendAsync(reply(message)));
 * }</pre>
 *
 * @param <T> The type of the message
 * @author Denis Stepanov
 * @since 5.3.0
 * @see WebSocketRouteSpec#onMessage(io.micronaut.core.type.Argument, WebSocketMessageHandler)
 */
@Experimental
@FunctionalInterface
public interface WebSocketMessageHandler<T> {

    /**
     * Handle a message.
     *
     * @param message The message
     * @param session The session of the connection
     * @return A stage that completes when the message is handled, or {@code null} if it is handled
     * @throws Exception An error, passed to the {@link WebSocketRouteSpec#onError(WebSocketErrorHandler) error handler}
     */
    @Nullable CompletionStage<?> onMessage(T message, WebSocketSession session) throws Exception;
}
