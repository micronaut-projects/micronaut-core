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
package io.micronaut.http.context.event;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpVersion;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * An immutable record of how the server handled one request, for metrics, tracing and access
 * logging, carried by {@link HttpRequestTerminatedEvent} when the server keeps one. The Netty
 * server keeps it when {@code micronaut.server.netty.request-log-enabled} is set.
 * <p>
 * Times are {@link System#nanoTime()} values: only the differences between them are meaningful.
 * A fact the server implementation does not track is absent, never estimated.
 *
 * @since 5.3.0
 */
@Experimental
public interface HttpRequestLog {

    /**
     * @return When the server started processing the request. The Netty server reports the time
     * the request head arrived, so this equals {@link #headersReadNanos()}
     */
    OptionalLong requestStartNanos();

    /**
     * @return When the request headers were fully read
     */
    OptionalLong headersReadNanos();

    /**
     * @return When the response that is written was produced: by the route, an error route, the
     * default error response, or a fallback response of the server
     */
    OptionalLong responseProducedNanos();

    /**
     * @return When the last byte of the response was handed to the socket successfully. Absent
     * if the response did not complete
     */
    OptionalLong responseWrittenNanos();

    /**
     * @return The number of request body bytes read, after any content decoding
     */
    OptionalLong requestBodyBytes();

    /**
     * @return The number of response body bytes written, before any content encoding
     */
    OptionalLong responseBodyBytes();

    /**
     * @return The status code of the response that was written
     */
    OptionalInt status();

    /**
     * @return The HTTP version of the request
     */
    Optional<HttpVersion> httpVersion();

    /**
     * @return The URI template of the matched route, e.g. {@code /books/{id}}, rather than the
     * raw path, which keeps the cardinality of metric tags low
     */
    Optional<String> routeTemplate();

    /**
     * @return The first failure seen for the request, also when an error response was written
     * for it
     */
    Optional<Throwable> failure();

    /**
     * @return Where {@link #failure()} happened
     */
    Optional<FailurePhase> failurePhase();

    /**
     * @return Whether the response was written completely. An error response that was written
     * completely counts as completed
     */
    boolean isCompleted();

    /**
     * @return The total time from the start of the request until the response was written, in
     * nanoseconds, if both are known
     */
    default OptionalLong totalDurationNanos() {
        OptionalLong start = requestStartNanos();
        OptionalLong end = responseWrittenNanos();
        if (start.isPresent() && end.isPresent()) {
            return OptionalLong.of(end.getAsLong() - start.getAsLong());
        }
        return OptionalLong.empty();
    }

    /**
     * Where a request failed.
     */
    enum FailurePhase {
        /**
         * Reading or decoding the request failed.
         */
        READING_REQUEST,
        /**
         * The handler (filters, route, argument binding, response encoding) failed.
         */
        HANDLER,
        /**
         * Writing the response to the connection failed.
         */
        WRITING_RESPONSE,
        /**
         * The client closed the connection or reset the stream before the response was written.
         */
        CLIENT_DISCONNECT
    }
}
