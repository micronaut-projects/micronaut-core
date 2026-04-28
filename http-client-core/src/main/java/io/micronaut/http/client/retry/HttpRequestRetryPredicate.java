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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;

/**
 * Decides whether an HTTP request is safe to retry. Replace this bean to broaden or narrow the
 * default <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-idempotent-methods">RFC 9110</a>
 * idempotency policy without modifying the retry filter.
 *
 * @since 5.0.0
 */
@FunctionalInterface
public interface HttpRequestRetryPredicate {

    /**
     * The {@code Idempotency-Key} request header — a de facto convention (see the expired
     * <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/">
     * draft-ietf-httpapi-idempotency-key-header</a>) used to mark otherwise non-idempotent
     * requests as safe to retry.
     */
    String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * @param request The request
     * @return {@code true} if the request may be retried on a transport or response failure
     */
    boolean isRetryable(HttpRequest<?> request);

    /**
     * Default predicate matching {@link HttpMethod#isIdempotent()} or any request carrying an
     * {@value #IDEMPOTENCY_KEY_HEADER} header.
     *
     * @return The default predicate
     */
    static HttpRequestRetryPredicate rfc9110() {
        return request -> request.getMethod().isIdempotent()
            || request.getHeaders().contains(IDEMPOTENCY_KEY_HEADER);
    }
}
