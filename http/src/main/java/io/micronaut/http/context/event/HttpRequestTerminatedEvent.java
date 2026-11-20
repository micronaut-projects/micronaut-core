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
package io.micronaut.http.context.event;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * An event fired when an {@link HttpRequest} is finalized by the server, once the server is done
 * with the request (the response has been handed to the transport, which does not mean it was
 * delivered, or the request was discarded). Consumers of the event should generally not perform
 * I/O, instead this is designed for tracing of headers, URI etc.
 * <p>
 * Listeners are invoked on the thread the server selects for request handling, which with the
 * default {@code micronaut.server.thread-selection} ({@code MANUAL}, and also with {@code AUTO})
 * is the Netty event loop of the connection. A listener that blocks or does slow work delays every
 * other connection on that event loop. Listeners that need to block must hand off, for example with {@code @Async} on the
 * {@code @EventListener} method, or the server can be configured with
 * {@code micronaut.server.thread-selection=BLOCKING} which moves the listeners (and controllers)
 * off the event loop.
 *
 * @author graemerocher
 * @since 1.2.0
 */
public class HttpRequestTerminatedEvent extends ApplicationEvent {

    private final transient @Nullable HttpRequestLog requestLog;

    /**
     * @param request The request. Never null.
     */
    public HttpRequestTerminatedEvent(HttpRequest<?> request) {
        this(request, null);
    }

    /**
     * @param request    The request. Never null.
     * @param requestLog The completion record of the request, if the server keeps one
     * @since 5.3.0
     */
    public HttpRequestTerminatedEvent(HttpRequest<?> request, @Nullable HttpRequestLog requestLog) {
        super(request);
        this.requestLog = requestLog;
    }

    /**
     * The completion record of the request: timings, byte counts, status, route template and
     * failure. Absent if the server implementation does not keep one, e.g. for a request that
     * was not served by the HTTP server itself.
     *
     * @return The record
     * @since 5.3.0
     */
    public Optional<HttpRequestLog> getRequestLog() {
        return Optional.ofNullable(requestLog);
    }

    @Override
    public HttpRequest<?> getSource() {
        return (HttpRequest<?>) super.getSource();
    }
}
