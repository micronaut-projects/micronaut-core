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
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * The outcome of one attempt of an operation, on which a {@link RetryRule} decides whether to
 * retry it: unlike a {@link io.micronaut.retry.annotation.RetryPredicate}, which only sees a
 * {@link Throwable}, a rule also sees an attempt that produced a result, e.g. an HTTP response
 * with a status code, and knows whether a failed attempt was processed at all.
 *
 * @param kind       What the attempt ended with
 * @param code       The code of the result, e.g. an HTTP status code, for {@link Kind#RESULT},
 *                   else {@code -1}
 * @param retryAfter The delay the result asks to wait before a retry, e.g. the
 *                   {@code Retry-After} header of an HTTP response, or {@code null}
 * @param failure    The failure, for every kind but {@link Kind#RESULT}, where it is {@code null}
 * @since 5.3.0
 */
@Internal
public record RetryOutcome(Kind kind, int code, @Nullable Duration retryAfter, @Nullable Throwable failure) {

    /**
     * @param kind       What the attempt ended with
     * @param code       The code of the result, or {@code -1}
     * @param retryAfter The delay the result asks to wait before a retry, or {@code null}
     * @param failure    The failure, or {@code null} for a result
     */
    public RetryOutcome {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.RESULT) {
            if (failure != null) {
                throw new IllegalArgumentException("A result has no failure");
            }
        } else {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * @param code The code of the result, e.g. an HTTP status code
     * @return The outcome of an attempt that produced a result
     */
    public static RetryOutcome result(int code) {
        return result(code, null);
    }

    /**
     * @param code       The code of the result, e.g. an HTTP status code
     * @param retryAfter The delay the result asks to wait before a retry, or {@code null}
     * @return The outcome of an attempt that produced a result
     */
    public static RetryOutcome result(int code, @Nullable Duration retryAfter) {
        return new RetryOutcome(Kind.RESULT, code, retryAfter, null);
    }

    /**
     * @param failure The failure
     * @return The outcome of an attempt that failed, maybe after it was processed
     */
    public static RetryOutcome failure(Throwable failure) {
        return new RetryOutcome(Kind.FAILURE, -1, null, failure);
    }

    /**
     * @param failure The failure
     * @return The outcome of an attempt that failed before it was processed at all, e.g. a
     * request whose connection could not be opened: it can be sent again safely
     */
    public static RetryOutcome unprocessed(Throwable failure) {
        return new RetryOutcome(Kind.UNPROCESSED, -1, null, failure);
    }

    /**
     * @param failure The timeout failure
     * @return The outcome of an attempt that did not complete in time
     */
    public static RetryOutcome timeout(Throwable failure) {
        return new RetryOutcome(Kind.TIMEOUT, -1, null, failure);
    }

    /**
     * What an attempt ended with.
     */
    public enum Kind {
        /**
         * The attempt produced a result, with a {@link #code()}.
         */
        RESULT,
        /**
         * The attempt failed, and may have been processed, e.g. a connection reset after a
         * request was sent.
         */
        FAILURE,
        /**
         * The attempt failed before it was processed at all.
         */
        UNPROCESSED,
        /**
         * The attempt did not complete in time.
         */
        TIMEOUT
    }
}
