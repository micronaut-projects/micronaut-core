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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.body.CloseableByteBody;
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
    @Nullable
    // guarded by this
    private transient CloseableByteBody unsentBody;
    private volatile boolean bodySent;

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
     * Take the body of the request that was never read, if the exchange asked for it, see
     * {@code RawRequestOptions#isReturnUnsentBody()}. The caller owns it and closes it.
     *
     * @return The body, empty if there is none or it was taken already
     * @since 5.3.0
     */
    @Experimental
    public final Optional<CloseableByteBody> takeUnsentBody() {
        CloseableByteBody body;
        synchronized (this) {
            body = unsentBody;
            unsentBody = null;
        }
        return Optional.ofNullable(body);
    }

    /**
     * Hand the body that was never read back to the caller. <b>Internal API.</b>
     *
     * @param body The body
     * @return Whether it was handed back; {@code false} if this exception carries one already
     * @since 5.3.0
     */
    @Internal
    public final synchronized boolean returnUnsentBody(CloseableByteBody body) {
        if (unsentBody != null) {
            return false;
        }
        unsentBody = body;
        return true;
    }

    /**
     * Mark that the body was sent or released before this request failed, e.g. sent to a server
     * that redirected it, or replaced by a filter. <b>Internal API.</b>
     *
     * @since 5.3.0
     */
    @Internal
    public final void markBodySent() {
        bodySent = true;
    }

    /**
     * @return Whether the request was surely not read at all, e.g. the connection could not be
     * opened, so that its body is untouched. A request that was redirected is not: its body went
     * to the server that redirected it. Neither is one whose body a filter replaced.
     * @since 5.3.0
     */
    @Experimental
    public final boolean isBodyUntouched() {
        return !bodySent && (reason == Reason.CONNECT || reason == Reason.CONNECT_TIMEOUT || reason == Reason.POOL_ACQUIRE);
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
