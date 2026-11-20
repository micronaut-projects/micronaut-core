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
package io.micronaut.http.server.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * Thrown when a {@link RequestLimiter} rejects a request. It is answered through the normal error
 * handling, by default with its status and a {@code Retry-After} header when known, see
 * {@link RequestLimitExceededExceptionHandler}. It carries no stack trace.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RequestLimitExceededException extends HttpStatusException {

    private final String limiterName;
    private final @Nullable Duration retryAfter;

    /**
     * @param limiterName The name of the limiter that rejected the request
     * @param status      The status of the response
     * @param retryAfter  When the client may try again, or {@code null}
     */
    public RequestLimitExceededException(String limiterName, HttpStatus status, @Nullable Duration retryAfter) {
        super(status, "Request limit [" + limiterName + "] exceeded");
        this.limiterName = limiterName;
        this.retryAfter = retryAfter;
    }

    /**
     * @return The name of the limiter that rejected the request
     */
    public String getLimiterName() {
        return limiterName;
    }

    /**
     * @return When the client may try again
     */
    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // a rejection is expected under load, a stack trace would only cost time
        return this;
    }
}
