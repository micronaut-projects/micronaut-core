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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * The optional instrumentation of one request: what the transport measures for the request log
 * when it is enabled, see {@link RequestHandler#recordsRequestHeadTime()}, and the JFR event of an
 * HTTP/1 request while a recording is active. Kept apart from the state of the connection, in one
 * object, so that requests cost nothing extra while neither is in use. Updated and read on the
 * event loop of the connection.
 *
 * @since 5.3.0
 */
@Internal
final class RequestStats {
    /**
     * Whether the request log is enabled. Otherwise the counts are not reported.
     */
    final boolean logged;
    /**
     * When the head of the request arrived, a {@link System#nanoTime()} value.
     */
    final long headNanos;
    /**
     * Request body bytes read, after content decoding.
     */
    long requestBytes;
    /**
     * Response body bytes handed to the connection, before content encoding.
     */
    long responseBytes;
    /**
     * The JFR event of an HTTP/1 request, or {@code null} if no recording was active when the
     * request started.
     */
    @Nullable
    Http1RequestEvent http1Event;

    /**
     * @param logged Whether the request log is enabled
     */
    RequestStats(boolean logged) {
        this.logged = logged;
        this.headNanos = logged ? System.nanoTime() : Long.MIN_VALUE;
    }
}
