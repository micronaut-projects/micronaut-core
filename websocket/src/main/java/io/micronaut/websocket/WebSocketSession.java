/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.MediaType;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
     * @return Only the attributes of the session
     */
    MutableConvertibleValues<Object> getAttributes();

    /**
     * Whether the session is open.
     * @return True if it is
     */
    boolean isOpen();

    /**
     * Whether the session is writable. It may not be writable, if the buffer is currently full
     * @return True if it is
     */
    boolean isWritable();

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
     * Send the given message to the remote peer.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link OnOpen} and {@link OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    <T> Publisher<T> send(T message, MediaType mediaType);

    /**
     * Send the given message to the remote peer asynchronously.
     *
     * @param message The message
     *  @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    <T> CompletableFuture<T> sendAsync(T message, MediaType mediaType);

    /**
     * Send the given message to the remote peer synchronously.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     */
    default void sendSync(Object message, MediaType mediaType) {
        try {
            sendAsync(message, mediaType).get();
        } catch (InterruptedException e) {
            throw new WebSocketSessionException("Send Interrupted");
        } catch (ExecutionException e) {
            throw new WebSocketSessionException("Send Failure: " + e.getMessage(), e);
        }
    }

    /**
     * Send the given message to the remote peer.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link OnOpen} and {@link OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    default <T> Publisher<T> send(T message) {
        return send(message, MediaType.APPLICATION_JSON_TYPE);
    }

    /**
     * Send the given message to the remote peer asynchronously.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    default <T> CompletableFuture<T> sendAsync(T message) {
        return sendAsync(message, MediaType.APPLICATION_JSON_TYPE);
    }

    /**
     * Send the given message to the remote peer synchronously.
     *
     * @param message The message
     */
    default void sendSync(Object message) {
        sendSync(message, MediaType.APPLICATION_JSON_TYPE);
    }

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
