/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.websocket.event;

import io.micronaut.websocket.WebSocketSession;

/**
 * An event fired after a WebSocket message has been processed.
 *
 * @param <T> The message type
 * @author graemerocher
 * @since 1.0
 */
public class WebSocketMessageProcessedEvent<T> extends WebSocketEvent {

    private T message;

    /**
     * Default constructor.
     *
     * @param session The web socket session
     * @param message The message that was processed
     */
    public WebSocketMessageProcessedEvent(WebSocketSession session, T message) {
        super(session);
        this.message = message;
    }

    /**
     * @return The message that was processed
     */
    public T getMessage() {
        return message;
    }
}
