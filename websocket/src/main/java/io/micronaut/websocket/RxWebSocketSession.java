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
package io.micronaut.websocket;

import io.micronaut.http.MediaType;
import io.reactivex.Flowable;

import java.util.Set;

/**
 * Generalization of the {@link WebSocketSession} interface for RxJava.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface RxWebSocketSession extends WebSocketSession {

    /**
     * The current open sessions.
     * @see WebSocketSession#getOpenSessions()
     * @return The open sessions
     */
    @Override
    Set<? extends RxWebSocketSession> getOpenSessions();

    /**
     * Broadcast a message and return a {@link Flowable}.
     *
     * @param message The message
     * @param mediaType The media type
     * @param <T> The message generic type
     * @return The {@link Flowable}
     */
    @Override
    <T> Flowable<T> send(T message, MediaType mediaType);
}
