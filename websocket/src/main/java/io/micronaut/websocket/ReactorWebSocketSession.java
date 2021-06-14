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
package io.micronaut.websocket;

import io.micronaut.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Generalization of the {@link WebSocketSession} interface for Project Reactor.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ReactorWebSocketSession extends WebSocketSession {

    /**
     * The current open sessions.
     * @see WebSocketSession#getOpenSessions()
     * @return The open sessions
     */
    @Override
    Set<? extends ReactorWebSocketSession> getOpenSessions();

    /**
     * Broadcast a message and return a {@link Flux}.
     *
     * @param message The message
     * @param mediaType The media type
     * @param <T> The message generic type
     * @return The {@link Flux}
     */
    @Override
    <T> Flux<T> send(T message, MediaType mediaType);
}
