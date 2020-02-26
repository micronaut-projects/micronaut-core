/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.websocket.interceptor;

import io.micronaut.websocket.WebSocketSession;

/**
 * Interface used by {@link io.micronaut.websocket.annotation.ClientWebSocket} to gain access to the underlying {@link WebSocketSession}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketSessionAware {

    /**
     * Sets the {@link WebSocketSession}.
     *
     * @param session The session.
     */
    void setWebSocketSession(WebSocketSession session);
}
