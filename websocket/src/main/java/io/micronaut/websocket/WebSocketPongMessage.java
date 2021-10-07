package io.micronaut.websocket;

import io.micronaut.core.io.buffer.ByteBuffer;

/**
 * Special message class that can be accepted by a {@link io.micronaut.websocket.annotation.OnMessage @OnMessage}
 * method to listen to WebSocket pongs.
 */
public final class WebSocketPongMessage {
    private final ByteBuffer<?> content;

    /**
     * @param content The content of the pong message.
     */
    public WebSocketPongMessage(ByteBuffer<?> content) {
        this.content = content;
    }

    /**
     * @return The content of the pong message. This buffer may be released after the message handler has completed.
     */
    public ByteBuffer<?> getContent() {
        return content;
    }
}
