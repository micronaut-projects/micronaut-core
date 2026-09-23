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
 * Called when a handler of a WebSocket route fails, or its connection fails, like an
 * {@link io.micronaut.websocket.annotation.OnError} method. Without an error handler, the
 * connection is closed with {@link io.micronaut.websocket.CloseReason#INTERNAL_ERROR}; with one,
 * the error handler decides, e.g. with {@link WebSocketSession#close(io.micronaut.websocket.CloseReason)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see WebSocketRouteSpec#onError(WebSocketErrorHandler)
 */
@Experimental
@FunctionalInterface
public interface WebSocketErrorHandler {

    /**
     * Handle an error.
     *
     * @param error   The error
     * @param session The session of the connection
     * @return A stage that completes when the handler is done, or {@code null} if it is done
     * @throws Exception An error, which is logged, and closes the connection
     */
    @Nullable CompletionStage<?> onError(Throwable error, WebSocketSession session) throws Exception;
}
