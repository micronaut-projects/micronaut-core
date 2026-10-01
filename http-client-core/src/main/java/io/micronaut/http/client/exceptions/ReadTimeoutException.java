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
 * elapsed while the response was awaited ({@link #TIMEOUT_EXCEPTION}), or while its body was read
 * ({@link #BODY_TIMEOUT_EXCEPTION}).
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class ReadTimeoutException extends HttpClientException {

    /**
     * The timeout elapsed before the response headers arrived: the server may or may not have
     * processed the request.
     */
    public static final ReadTimeoutException TIMEOUT_EXCEPTION = new ReadTimeoutException("Read Timeout", false);

    /**
     * The timeout elapsed while the response body was read: the response had already arrived,
     * and its body is incomplete.
     *
     * @since 5.3.0
     */
    public static final ReadTimeoutException BODY_TIMEOUT_EXCEPTION = new ReadTimeoutException("Read Timeout while reading the response body", true);

    private final boolean headersReceived;

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
