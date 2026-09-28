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
package io.micronaut.retry;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Validates the configured named circuit breakers when the context starts, so that an invalid
 * one, e.g. with a fail-on that is not an exception type, fails the startup rather than the
 * first use of its name. Only present when a circuit breaker is configured.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Context
@Requires(property = NamedCircuitBreakerConfiguration.PREFIX)
final class NamedCircuitBreakerValidator {

    /**
     * @param configurations The configurations of the named circuit breakers
     * @throws IllegalArgumentException if a circuit breaker is invalid
     */
    NamedCircuitBreakerValidator(List<NamedCircuitBreakerConfiguration> configurations) {
        for (NamedCircuitBreakerConfiguration configuration : configurations) {
            configuration.toPolicy();
        }
    }
}
