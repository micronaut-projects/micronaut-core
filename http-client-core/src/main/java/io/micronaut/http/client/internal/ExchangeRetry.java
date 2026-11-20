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
package io.micronaut.http.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The retry of the exchanges of a client, see
 * {@link io.micronaut.http.client.ExchangeRetryConfiguration}: it decides from the outcome of
 * an attempt whether, and when, the exchange is attempted again, see
 * {@link PolicyExchangeRetry}.
 *
 * @since 5.3.0
 */
@Internal
public interface ExchangeRetry {

    /**
     * @return The maximum number of attempts, the first one included
     */
    int maxAttempts();

    /**
     * @return The timeout of an attempt, or {@code null}
     */
    @Nullable Duration attemptTimeout();

    /**
     * @return The request header with the number of the retry, or {@code null}
     */
    @Nullable String attemptHeader();

    /**
     * @param request      The request
     * @param attempt      The number of the attempt that ended, from {@code 1}
     * @param outcome      Its outcome
     * @param overallDelay The time waited between the attempts so far
     * @return The delay before the next attempt, or {@code null} if there is none
     */
    @Nullable Duration retryDelay(HttpRequest<?> request, int attempt, Outcome outcome, Duration overallDelay);

    /**
     * The outcome of an attempt.
     *
     * @param kind       What the attempt ended with
     * @param status     The status of the response, or {@code -1}
     * @param retryAfter The delay the {@code Retry-After} header of the response asks for, or
     *                   {@code null}
     * @param failure    The failure, or {@code null} for a response
     */
    record Outcome(Kind kind, int status, @Nullable Duration retryAfter, @Nullable Throwable failure) {

        /**
         * @param status     The status
         * @param retryAfter The {@code Retry-After} delay, or {@code null}
         * @return The outcome of a response
         */
        public static Outcome response(int status, @Nullable Duration retryAfter) {
            return new Outcome(Kind.RESPONSE, status, retryAfter, null);
        }

        /**
         * @param kind    The kind of the failure
         * @param failure The failure
         * @return The outcome of a failure
         */
        public static Outcome failure(Kind kind, Throwable failure) {
            return new Outcome(kind, -1, null, failure);
        }
    }

    /**
     * What an attempt ended with.
     */
    enum Kind {
        /**
         * A response.
         */
        RESPONSE,
        /**
         * A failure, after the request may have been processed.
         */
        FAILURE,
        /**
         * A failure before the request was processed.
         */
        UNPROCESSED,
        /**
         * A timeout.
         */
        TIMEOUT
    }
}
