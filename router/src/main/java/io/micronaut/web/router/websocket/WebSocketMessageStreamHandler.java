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
import io.micronaut.websocket.WebSocketSession;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * Called once for each connection to a WebSocket route, with the messages of the connection as a
 * stream. The messages are decoded like for a {@link WebSocketMessageHandler}, and a message is
 * handled once the subscriber requested it. The handler subscribes before it is done.
 *
 * <pre>{@code
 * ws.onMessageStream(String.class, (session, messages) -> session.sendAllAsync(Flux.from(messages).map(String::toUpperCase)));
 * }</pre>
 *
 * @param <T> The type of the messages
 * @author Denis Stepanov
 * @since 5.3.0
 * @see WebSocketEndpointSpec#onMessageStream(io.micronaut.core.type.Argument, WebSocketMessageStreamHandler)
 */
@Experimental
@FunctionalInterface
public interface WebSocketMessageStreamHandler<T> {

    /**
     * Handle the messages of a connection.
     *
     * @param session  The session of the connection
     * @param messages The messages of the connection, which completes when the connection closes,
     *                 and has a single subscriber
     * @return A stage that completes when the handler is done, or {@code null} if it is done
     * @throws Exception An error, passed to the {@link WebSocketEndpointSpec#onError(WebSocketErrorHandler) error handler}
     */
    @Nullable CompletionStage<?> onMessageStream(WebSocketSession session, Publisher<T> messages) throws Exception;
}
