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
 * An exception thrown when a read timeout occurs.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class ReadTimeoutException extends HttpClientException {

    /**
     * Shared instance.
     *
     * @deprecated This shared instance captures its stack trace at static initialization time
     * and is reused across concurrent requests. Use {@link #ReadTimeoutException()} instead so
     * that the stack trace points to the request that actually timed out and so the exception
     * can carry per-request metadata such as the service id.
     */
    @Deprecated(since = "4.9.0")
    public static final ReadTimeoutException TIMEOUT_EXCEPTION = new ReadTimeoutException(true);

    /**
     * Create a new read timeout exception. The stack trace is captured at the point of creation,
     * so it points to the client/request path that timed out.
     */
    public ReadTimeoutException() {
        super("Read Timeout");
    }

    private ReadTimeoutException(boolean shared) {
        super("Read Timeout", null, shared);
    }
}
