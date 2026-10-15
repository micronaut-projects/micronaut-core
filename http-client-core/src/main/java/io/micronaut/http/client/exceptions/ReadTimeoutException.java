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
package io.micronaut.http.client.exceptions;

/**
 * An exception thrown when a read timeout occurs. {@link #isHeadersReceived()} tells whether it
 * elapsed while the response was awaited, or while its body was read.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class ReadTimeoutException extends HttpClientException {

    /**
     * Shared instance for a timeout that elapsed before the response headers arrived: the server
     * may or may not have processed the request.
     *
     * @deprecated This shared instance captures its stack trace at static initialization time and
     * is reused across concurrent requests. Prefer {@link #ReadTimeoutException()} (or
     * {@link #ReadTimeoutException(boolean)}) so that the stack trace points to the request that
     * actually timed out and so the exception can carry per-request metadata such as the service id.
     */
    @Deprecated(since = "5.3.0")
    public static final ReadTimeoutException TIMEOUT_EXCEPTION = new ReadTimeoutException("Read Timeout", false);

    /**
     * Shared instance for a timeout that elapsed while the response body was read: the response had
     * already arrived, and its body is incomplete.
     *
     * @since 5.3.0
     * @deprecated For the same reasons as {@link #TIMEOUT_EXCEPTION}; prefer
     * {@link #ReadTimeoutException(boolean)}.
     */
    @Deprecated(since = "5.3.0")
    public static final ReadTimeoutException BODY_TIMEOUT_EXCEPTION = new ReadTimeoutException("Read Timeout while reading the response body", true);

    private final boolean headersReceived;

    /**
     * Create a new read timeout exception for a timeout that elapsed before the response headers
     * arrived. The stack trace is captured at the point of creation.
     */
    public ReadTimeoutException() {
        this(false);
    }

    /**
     * Create a new read timeout exception. The stack trace is captured at the point of creation,
     * so it points to the client/request path that timed out.
     *
     * @param headersReceived whether the response headers had been received when the timeout
     *                         elapsed, i.e. the timeout elapsed while the response body was read
     * @since 5.3.0
     */
    public ReadTimeoutException(boolean headersReceived) {
        super(headersReceived ? "Read Timeout while reading the response body" : "Read Timeout");
        this.headersReceived = headersReceived;
    }

    private ReadTimeoutException(String message, boolean headersReceived) {
        super(message, null, true);
        this.headersReceived = headersReceived;
    }

    /**
     * @return Whether the response headers had been received when the timeout elapsed, i.e. the
     * timeout elapsed while the response body was read; {@code false} if it elapsed while the
     * response was awaited
     * @since 5.3.0
     */
    public boolean isHeadersReceived() {
        return headersReceived;
    }
}
