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
package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.time.Duration;

/**
 * The route did not produce its response within its request timeout, see
 * {@link io.micronaut.http.annotation.RequestTimeout}. It is answered with a
 * {@code 503 Service Unavailable} unless an error route or an exception handler handles it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
public final class RequestTimeoutException extends HttpStatusException {

    private final transient Duration timeout;

    /**
     * @param timeout The timeout that expired
     */
    public RequestTimeoutException(Duration timeout) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Request timed out after " + timeout.toMillis() + "ms");
        this.timeout = timeout;
    }

    /**
     * @return The timeout of the route that expired
     */
    public Duration getTimeout() {
        return timeout;
    }
}
