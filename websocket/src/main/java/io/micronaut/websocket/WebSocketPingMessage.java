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
package io.micronaut.websocket;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;

import java.util.Objects;

/**
 * A ping a WebSocket received, the counterpart of a {@link WebSocketPongMessage}. The server
 * answers each ping with a pong itself. Only the ping handler of a WebSocket route of the route
 * builder receives pings for now ({@code WebSocketEndpointSpec#onPing}), not the methods of a
 * {@link io.micronaut.websocket.annotation.ServerWebSocket} bean or a client.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class WebSocketPingMessage {
    private final ByteBuffer<?> content;

    /**
     * @param content The content of the ping message.
     */
    public WebSocketPingMessage(ByteBuffer<?> content) {
        Objects.requireNonNull(content, "content");
        this.content = content;
    }

    /**
     * @return The content of the ping message. This buffer may be released after the message handler has completed.
     */
    public ByteBuffer<?> getContent() {
        return content;
    }
}
