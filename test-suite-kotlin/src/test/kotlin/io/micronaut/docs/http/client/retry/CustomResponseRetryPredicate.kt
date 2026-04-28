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
package io.micronaut.docs.http.client.retry

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.retry.HttpResponseRetryPredicate
import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Replaces(HttpResponseRetryPredicate::class)
@Requires(property = "spec.name", value = "CustomResponseRetryPredicateSpec") // <1>
class CustomResponseRetryPredicate : HttpResponseRetryPredicate {

    override fun shouldRetry(failure: Throwable): Boolean {
        // Extend the default policy: also retry HTTP 425 Too Early.
        if (HttpResponseRetryPredicate.rfc9110().shouldRetry(failure)) {
            return true
        }
        return failure is HttpClientResponseException && failure.status == HttpStatus.TOO_EARLY
    }
}
// end::class[]
