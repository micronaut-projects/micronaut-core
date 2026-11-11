/*
 * Copyright 2017-2023 original authors
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
 * Exception raised when the connection is closed during the response.
 *
 * @since 4.1.0
 * @author Jonas Konrad
 */
public class ResponseClosedException extends HttpClientException {
    private final boolean headersReceived;

    public ResponseClosedException(String message) {
        this(message, false);
    }

    /**
     * @param message         The message
     * @param headersReceived Whether the response headers had been received when the connection closed
     * @since 5.3.0
     */
    public ResponseClosedException(String message, boolean headersReceived) {
        super(message);
        this.headersReceived = headersReceived;
    }

    /**
     * @return Whether the response headers had been received when the connection closed, i.e.
     * the connection closed while the response body was read; {@code false} if it closed while
     * the response was awaited
     * @since 5.3.0
     */
    public boolean isHeadersReceived() {
        return headersReceived;
    }
}
