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
package io.micronaut.http.client.retry

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification
import spock.lang.Unroll

class HttpResponseRetryPredicateSpec extends Specification {

    @Unroll
    void "isRetryableStatus(#code) == #expected"() {
        expect:
        HttpResponseRetryPredicate.isRetryableStatus(code) == expected

        where:
        code | expected
        // 5xx — server-error range, retryable
        500  | true
        502  | true
        503  | true
        504  | true
        599  | true
        // 429 — rate-limited, retryable
        429  | true
        // 408 — request timeout, retryable
        408  | true
        // Other 4xx — terminal client errors, NOT retryable
        400  | false
        401  | false
        403  | false
        404  | false
        409  | false
        422  | false
        425  | false   // Too Early — not in default set; a custom predicate can opt in
        // 2xx / 3xx — not error responses
        200  | false
        301  | false
        // ≥ 600 — not valid HTTP statuses; bounded out of the 5xx range
        600  | false
        700  | false
    }

    void "default predicate retries 5xx HttpClientResponseException"() {
        given:
        def predicate = HttpResponseRetryPredicate.rfc9110()
        def failure = new HttpClientResponseException('boom',
            HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE))

        expect:
        predicate.shouldRetry(failure)
    }

    void "default predicate does NOT retry 404"() {
        given:
        def predicate = HttpResponseRetryPredicate.rfc9110()
        def failure = new HttpClientResponseException('not found',
            HttpResponse.status(HttpStatus.NOT_FOUND))

        expect:
        !predicate.shouldRetry(failure)
    }

    void "default predicate retries transport-level HttpClientException"() {
        given:
        def predicate = HttpResponseRetryPredicate.rfc9110()
        def failure = new HttpClientException('connect timeout')

        expect:
        predicate.shouldRetry(failure)
    }

    void "default predicate does NOT retry arbitrary RuntimeException"() {
        given:
        def predicate = HttpResponseRetryPredicate.rfc9110()

        expect:
        !predicate.shouldRetry(new IllegalStateException('not transport related'))
    }
}
