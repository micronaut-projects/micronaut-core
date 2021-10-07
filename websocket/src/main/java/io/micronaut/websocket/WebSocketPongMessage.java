/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;

import java.util.Objects;

/**
 * Special message class that can be accepted by a {@link io.micronaut.websocket.annotation.OnMessage @OnMessage}
 * method to listen to WebSocket pongs.
 *
 * @since 3.1
 * @author Jonas Konrad
 */
public final class WebSocketPongMessage {
    private final ByteBuffer<?> content;

    /**
     * @param content The content of the pong message.
     */
    public WebSocketPongMessage(@NonNull ByteBuffer<?> content) {
        Objects.requireNonNull(content, "content");
        this.content = content;
    }

    /**
     * @return The content of the pong message. This buffer may be released after the message handler has completed.
     */
    @NonNull
    public ByteBuffer<?> getContent() {
        return content;
    }
}
