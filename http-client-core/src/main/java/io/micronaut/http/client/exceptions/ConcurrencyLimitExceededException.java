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
 * Thrown when an exchange of a service client is over the {@code max-concurrent-requests} limit of
 * the service. The request was not sent.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ConcurrencyLimitExceededException extends HttpClientException {

    private final String limiterName;

    /**
     * @param limiterName The name of the limiter, the service id
     * @param message     The message
     */
    public ConcurrencyLimitExceededException(String limiterName, String message) {
        super(message);
        this.limiterName = limiterName;
    }

    /**
     * @return The name of the limiter, the service id
     */
    public String getLimiterName() {
        return limiterName;
    }
}
