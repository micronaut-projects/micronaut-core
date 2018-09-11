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

import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an open WebSocket connection. Based largely on {@code javax.websocket} and likely to be able to implement the spec in the future.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketSession extends MutableConvertibleValues<Object>, AutoCloseable {

    /**
     * The ID of the session.
     *
     * @return The ID of the session
     */
    String getId();

    /**
     * Whether the session is open.
     * @return True if it is
     */
    boolean isOpen();

    /**
     * Whether the connection is secure.
     *
     * @return True if it is secure
     */
    boolean isSecure();

    /**
     * The current open sessions.
     *
     * @return The open sessions
     */
    Set<? extends WebSocketSession> getOpenSessions();

    /**
     * The request URI this session was opened under.
     *
     * @return The request URI
     */
    URI getRequestURI();

    /**
     * The protocol version of the WebSocket protocol currently being used.
     *
     * @return The protocol version
     */
    String getProtocolVersion();

    /**
     * Broadcast the given message to the remote peer.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    <T> Publisher<T> send(T message);

    /**
     * Broadcast the given message to the remote peer asynchronously.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    <T> CompletableFuture<T> sendAsync(T message);

    /**
     * Broadcast the given message to the remote peer synchronously.
     *
     * @param message The message
     */
    void sendSync(Object message);

    /**
     * The subprotocol if one is used.
     * @return The subprotocol
     */
    default Optional<String> getSubprotocol() {
        return Optional.empty();
    }

    /**
     * The request parameters used to create this session.
     *
     * @return The request parameters
     */
    default ConvertibleMultiValues<String> getRequestParameters() {
        return ConvertibleMultiValues.empty();
    }

    /**
     * Any matching URI path variables.
     *
     * @return The path variables
     */
    default ConvertibleValues<Object> getUriVariables() {
        return ConvertibleValues.empty();
    }

    /**
     * The user {@link Principal} used to create the session.
     *
     * @return The {@link Principal}
     */
    default Optional<Principal> getUserPrincipal() {
        return Optional.empty();
    }

    @Override
    void close();

    /**
     * Close the session with the given event.
     *
     * @param closeReason The close event
     */
    void close(CloseReason closeReason);
}
