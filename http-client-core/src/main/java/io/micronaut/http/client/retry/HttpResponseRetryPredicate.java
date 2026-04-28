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
package io.micronaut.http.client.retry;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

/**
 * Decides whether a transport error or error-status response warrants a retry. Replace this
 * bean to extend the default policy.
 *
 * <p>Default policy retries:</p>
 * <ul>
 *     <li>Transport failures ({@link HttpClientException} that is not an
 *     {@link HttpClientResponseException})</li>
 *     <li>{@code 5xx} —
 *     <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-server-error-5xx">RFC 9110 §15.6</a></li>
 *     <li>{@code 429 Too Many Requests} —
 *     <a href="https://www.rfc-editor.org/rfc/rfc6585.html#section-4">RFC 6585 §4</a></li>
 *     <li>{@code 408 Request Timeout} —
 *     <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-408-request-timeout">RFC 9110 §15.5.9</a></li>
 * </ul>
 *
 * <p>Other 4xx are deliberately excluded: a filter-level retry shares one read-timeout budget
 * with the original request (unlike AOP {@link io.micronaut.retry.annotation.Retryable}, which
 * gets a fresh budget per attempt), so retrying a terminal 4xx burns the budget on back-off
 * delays and surfaces as {@link io.micronaut.http.client.exceptions.ReadTimeoutException}
 * instead of the original status.</p>
 *
 * @since 5.0.0
 */
@FunctionalInterface
public interface HttpResponseRetryPredicate {

    /**
     * @param throwable A transport error, or an {@link HttpClientResponseException} carrying an
     *                  error-status response
     * @return {@code true} if the failure warrants a retry
     */
    boolean shouldRetry(Throwable throwable);

    /**
     * Returns {@code true} if the given status code is in the default retryable set
     * ({@code 5xx}, {@code 429}, {@code 408}). See the type-level Javadoc for rationale and
     * RFC references.
     *
     * @param statusCode The HTTP status code
     * @return {@code true} if the status is retryable by default
     */
    static boolean isRetryableStatus(int statusCode) {
        return statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.getCode()
            || statusCode == HttpStatus.TOO_MANY_REQUESTS.getCode()
            || statusCode == HttpStatus.REQUEST_TIMEOUT.getCode();
    }

    /**
     * The default predicate, applying the policy described in the type-level Javadoc.
     *
     * @return The default predicate
     */
    static HttpResponseRetryPredicate rfc9110() {
        return throwable -> {
            if (throwable instanceof HttpClientResponseException ex) {
                HttpResponse<?> response = ex.getResponse();
                return isRetryableStatus(response.getStatus().getCode());
            }
            return throwable instanceof HttpClientException;
        };
    }
}
