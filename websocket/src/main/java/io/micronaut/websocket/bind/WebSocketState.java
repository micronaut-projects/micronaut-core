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
package io.micronaut.websocket.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;

/**
 * Holder object used to bind WebSocket state.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class WebSocketState {

    private final WebSocketSession session;
    private final HttpRequest<?> originatingRequest;

    /**
     * Default constructor.
     *
     * @param session The session
     * @param originatingRequest The originating request
     */
    public WebSocketState(WebSocketSession session, HttpRequest<?> originatingRequest) {
        this.session = session;
        this.originatingRequest = originatingRequest;
    }

    /**
     * @return The session
     */
    public WebSocketSession getSession() {
        return session;
    }

    /**
     * @return The originating request
     */
    public HttpRequest<?> getOriginatingRequest() {
        return originatingRequest;
    }
}
