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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Provides default {@link HttpRequestRetryPredicate} and {@link HttpResponseRetryPredicate} beans.
 * Both can be replaced by declaring a user-defined bean of the same type.
 *
 * @since 5.0.0
 */
@Internal
@Factory
final class DefaultRetryPredicateFactory {

    @Bean
    @Singleton
    @Requires(missingBeans = HttpRequestRetryPredicate.class)
    HttpRequestRetryPredicate defaultRequestRetryPredicate() {
        return HttpRequestRetryPredicate.rfc9110();
    }

    @Bean
    @Singleton
    @Requires(missingBeans = HttpResponseRetryPredicate.class)
    HttpResponseRetryPredicate defaultResponseRetryPredicate() {
        return HttpResponseRetryPredicate.rfc9110();
    }
}
