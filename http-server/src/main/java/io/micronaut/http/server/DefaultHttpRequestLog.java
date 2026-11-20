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
package io.micronaut.http.server;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.context.event.HttpRequestLog;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mutable {@link HttpRequestLog} that a server implementation fills in, and publishes as an
 * immutable {@link #snapshot()}. The server-neutral
 * facts (status, route template, handler failure) are set by any server; transport facts (byte
 * counts, write completion) only by a server that tracks them. Unset facts read as absent.
 * <p>
 * Each field is written by one party and published with volatile writes. The record also joins
 * the two events that must both happen before the terminated event is published: the server is
 * done with the request, and the response completed, see {@link #arrive()}.
 *
 * @since 5.3.0
 */
@Internal
public final class DefaultHttpRequestLog implements HttpRequestLog {
    private static final long ABSENT = Long.MIN_VALUE;

    private final AtomicInteger pending = new AtomicInteger(2);
    private volatile long requestStartNanos = ABSENT;
    private volatile long headersReadNanos = ABSENT;
    private volatile long responseProducedNanos = ABSENT;
    private volatile long responseWrittenNanos = ABSENT;
    private volatile long requestBodyBytes = ABSENT;
    private volatile long responseBodyBytes = ABSENT;
    private volatile int status = -1;
    private volatile @Nullable HttpVersion httpVersion;
    private volatile @Nullable String routeTemplate;
    private @Nullable Throwable failure;
    private @Nullable FailurePhase failurePhase;
    private volatile boolean completed;

    /**
     * @param startNanos  When the request head was read, or {@link Long#MIN_VALUE} if unknown
     * @param httpVersion The HTTP version of the request, if known
     */
    public DefaultHttpRequestLog(long startNanos, @Nullable HttpVersion httpVersion) {
        this.requestStartNanos = startNanos;
        this.headersReadNanos = startNanos;
        this.httpVersion = httpVersion;
    }

    private static OptionalLong opt(long v) {
        return v == ABSENT ? OptionalLong.empty() : OptionalLong.of(v);
    }

    @Override
    public OptionalLong requestStartNanos() {
        return opt(requestStartNanos);
    }

    @Override
    public OptionalLong headersReadNanos() {
        return opt(headersReadNanos);
    }

    @Override
    public OptionalLong responseProducedNanos() {
        return opt(responseProducedNanos);
    }

    @Override
    public OptionalLong responseWrittenNanos() {
        return opt(responseWrittenNanos);
    }

    @Override
    public OptionalLong requestBodyBytes() {
        return opt(requestBodyBytes);
    }

    @Override
    public OptionalLong responseBodyBytes() {
        return opt(responseBodyBytes);
    }

    @Override
    public OptionalInt status() {
        int s = status;
        return s < 0 ? OptionalInt.empty() : OptionalInt.of(s);
    }

    @Override
    public Optional<HttpVersion> httpVersion() {
        return Optional.ofNullable(httpVersion);
    }

    @Override
    public Optional<String> routeTemplate() {
        return Optional.ofNullable(routeTemplate);
    }

    @Override
    public synchronized Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    @Override
    public synchronized Optional<FailurePhase> failurePhase() {
        return Optional.ofNullable(failurePhase);
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    /**
     * The handler produced the response that is written.
     *
     * @param nanos  The time
     * @param status The status code
     */
    public void responseProduced(long nanos, int status) {
        this.responseProducedNanos = nanos;
        this.status = status;
    }

    /**
     * @param routeTemplate The URI template of the matched route
     */
    public void routeTemplate(@Nullable String routeTemplate) {
        this.routeTemplate = routeTemplate;
    }

    /**
     * @param requestBodyBytes  The request body bytes, negative if unknown
     * @param responseBodyBytes The response body bytes, negative if unknown
     */
    public void bodyBytes(long requestBodyBytes, long responseBodyBytes) {
        if (requestBodyBytes >= 0) {
            this.requestBodyBytes = requestBodyBytes;
        }
        if (responseBodyBytes >= 0) {
            this.responseBodyBytes = responseBodyBytes;
        }
    }

    /**
     * The response was written completely.
     *
     * @param nanos The time
     */
    public void responseWritten(long nanos) {
        this.responseWrittenNanos = nanos;
        this.completed = true;
    }

    /**
     * Record a failure. The first one wins.
     *
     * @param phase The phase
     * @param cause The failure
     */
    public synchronized void fail(FailurePhase phase, Throwable cause) {
        if (failure == null) {
            failure = cause;
            failurePhase = phase;
        }
    }

    /**
     * One of the two parties is done: the server with the request, or the transport with the
     * response.
     *
     * @return {@code true} for the last of the two, which publishes the terminated event
     */
    public boolean arrive() {
        return pending.decrementAndGet() == 0;
    }

    /**
     * Skip waiting for the transport, e.g. when the completion of the response cannot be
     * observed.
     */
    public void transportDone() {
        arrive();
    }

    /**
     * @return Whether writing the response failed or the client went away
     */
    public synchronized boolean transportFailed() {
        return failurePhase == FailurePhase.WRITING_RESPONSE || failurePhase == FailurePhase.CLIENT_DISCONNECT;
    }

    /**
     * @return An immutable copy of the facts recorded so far, for the terminated event
     */
    public HttpRequestLog snapshot() {
        Throwable f;
        FailurePhase p;
        synchronized (this) {
            f = failure;
            p = failurePhase;
        }
        return new Snapshot(requestStartNanos, headersReadNanos, responseProducedNanos, responseWrittenNanos,
            requestBodyBytes, responseBodyBytes, status, httpVersion, routeTemplate, f, p, completed);
    }

    @Override
    public String toString() {
        return "HttpRequestLog{status=" + status + ", route=" + routeTemplate + ", completed=" + completed
            + ", failurePhase=" + failurePhase + ", requestBytes=" + requestBodyBytes + ", responseBytes=" + responseBodyBytes + "}";
    }

    /**
     * The immutable record the terminated event carries.
     *
     * @param requestStart     When the request started, or {@link #ABSENT}
     * @param headersRead      When the request head was read, or {@link #ABSENT}
     * @param responseProduced When the response was produced, or {@link #ABSENT}
     * @param responseWritten  When the response was written, or {@link #ABSENT}
     * @param requestBytes     The request body bytes, or {@link #ABSENT}
     * @param responseBytes    The response body bytes, or {@link #ABSENT}
     * @param statusCode       The status, or {@code -1}
     * @param version          The HTTP version
     * @param template         The route template
     * @param cause            The first failure
     * @param phase            The phase of the failure
     * @param done             Whether the response completed
     */
    private record Snapshot(long requestStart, long headersRead, long responseProduced, long responseWritten,
                            long requestBytes, long responseBytes, int statusCode,
                            @Nullable HttpVersion version, @Nullable String template,
                            @Nullable Throwable cause, @Nullable FailurePhase phase, boolean done) implements HttpRequestLog {

        @Override
        public OptionalLong requestStartNanos() {
            return opt(requestStart);
        }

        @Override
        public OptionalLong headersReadNanos() {
            return opt(headersRead);
        }

        @Override
        public OptionalLong responseProducedNanos() {
            return opt(responseProduced);
        }

        @Override
        public OptionalLong responseWrittenNanos() {
            return opt(responseWritten);
        }

        @Override
        public OptionalLong requestBodyBytes() {
            return opt(requestBytes);
        }

        @Override
        public OptionalLong responseBodyBytes() {
            return opt(responseBytes);
        }

        @Override
        public OptionalInt status() {
            return statusCode < 0 ? OptionalInt.empty() : OptionalInt.of(statusCode);
        }

        @Override
        public Optional<HttpVersion> httpVersion() {
            return Optional.ofNullable(version);
        }

        @Override
        public Optional<String> routeTemplate() {
            return Optional.ofNullable(template);
        }

        @Override
        public Optional<Throwable> failure() {
            return Optional.ofNullable(cause);
        }

        @Override
        public Optional<FailurePhase> failurePhase() {
            return Optional.ofNullable(phase);
        }

        @Override
        public boolean isCompleted() {
            return done;
        }

        @Override
        public String toString() {
            return "HttpRequestLog{status=" + statusCode + ", route=" + template + ", completed=" + done
                + ", failurePhase=" + phase + ", requestBytes=" + requestBytes + ", responseBytes=" + responseBytes + "}";
        }
    }
}
