/*
 * Copyright 2017-2018 original authors
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

import org.reactivestreams.Publisher;

import java.net.URI;

/**
 * Interface that provides a way to connect a client over WebSocket.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketClient {

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientEndpoint}
     * @param uri The URI or the WebSocket
     * @return A {@link Publisher} that emits the {@link WebSocketSession} on successful connect
     */
    Publisher<? extends WebSocketSession> connect(
            Class<?> clientEndpointType,
            URI uri
    );
}
