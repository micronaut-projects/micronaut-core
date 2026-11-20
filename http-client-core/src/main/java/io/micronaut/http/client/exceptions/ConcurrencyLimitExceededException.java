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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.annotation.Experimental;

/**
 * Thrown when an exchange of a service client could not get a permit of the
 * {@code max-concurrent-requests} limit of the service: the wait queue was full, or the wait
 * timed out. The request was not sent.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ConcurrencyLimitExceededException extends HttpClientException {

    private final String limiterName;
    private final boolean timedOut;

    /**
     * @param limiterName The name of the limiter, the service id
     * @param message     The message
     * @param timedOut    Whether the exchange waited in the queue and timed out, else the queue
     *                    was full
     */
    public ConcurrencyLimitExceededException(String limiterName, String message, boolean timedOut) {
        super(message);
        this.limiterName = limiterName;
        this.timedOut = timedOut;
    }

    /**
     * @return The name of the limiter, the service id
     */
    public String getLimiterName() {
        return limiterName;
    }

    /**
     * @return Whether the exchange waited in the queue and timed out, else the queue was full
     */
    public boolean isTimedOut() {
        return timedOut;
    }
}
