/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * The request was not sent: no byte of it reached the server, so the server did not process it.
 * Such a request can be sent again, whatever its method and body, without the server seeing it
 * twice, which is what a retry of a non-idempotent request needs to know. The {@link Reason}
 * says why it was not sent.
 * <p>{@link NoAvailableServiceException}, thrown when the load balancer has no instance to send
 * the request to, is the other exception of a request that was not sent:
 * {@link #isUnprocessed(Throwable)} covers both.
 * <p>{@link #getUri()} and {@link #getServiceInstance()} name the server the request was for,
 * when known, e.g. to exclude it from the next attempt.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@SuppressWarnings("java:S110") // the hierarchy depth comes from HttpClientException
public class UnprocessedRequestException extends HttpClientException {
    private final Reason reason;
    @Nullable
    private URI uri;
    @Nullable
    private transient ServiceInstance serviceInstance;
    private boolean targetSet;

    /**
     * @param reason  Why the request was not sent
     * @param message The message
     * @param cause   The cause, e.g. the connect exception
     */
    public UnprocessedRequestException(Reason reason, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * @return Why the request was not sent
     */
    public final Reason getReason() {
        return reason;
    }

    /**
     * @return The URI the request was for, if known
     */
    public final Optional<URI> getUri() {
        return Optional.ofNullable(uri);
    }

    /**
     * @return The service instance the request was for, if the request was load balanced
     */
    public final Optional<ServiceInstance> getServiceInstance() {
        return Optional.ofNullable(serviceInstance);
    }

    /**
     * Record the server the request was for. Only the first call has an effect.
     *
     * @param uri             The URI of the request
     * @param serviceInstance The selected service instance, if any
     */
    @Internal
    public final void setTarget(@Nullable URI uri, @Nullable ServiceInstance serviceInstance) {
        if (targetSet) {
            return;
        }
        targetSet = true;
        this.uri = uri;
        this.serviceInstance = serviceInstance;
    }

    /**
     * Whether an exception means the request was not sent to any server, so that it can be sent
     * again without a server processing it twice: this exception, or
     * {@link NoAvailableServiceException}.
     *
     * @param throwable The exception
     * @return Whether the request was not processed by a server
     */
    public static boolean isUnprocessed(@Nullable Throwable throwable) {
        return throwable instanceof UnprocessedRequestException || throwable instanceof NoAvailableServiceException;
    }

    /**
     * Why the request was not sent.
     */
    public enum Reason {
        /**
         * The connection to the server could not be opened, e.g. it was refused.
         */
        CONNECT,
        /**
         * The connection to the server could not be opened before the connect timeout.
         */
        CONNECT_TIMEOUT,
        /**
         * No connection could be acquired from the connection pool, e.g. too many requests are
         * waiting for one.
         */
        POOL_ACQUIRE,
        /**
         * The server refused the HTTP/2 stream ({@code REFUSED_STREAM}) before processing it,
         * e.g. because it is shutting down or has too many streams open.
         */
        STREAM_REFUSED,
        /**
         * The connection was closed, e.g. by the server after an idle period, before the request
         * could be written to it.
         */
        CLOSED_BEFORE_WRITE
    }
}
