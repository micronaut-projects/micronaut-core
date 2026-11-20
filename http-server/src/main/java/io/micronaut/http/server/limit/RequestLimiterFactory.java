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
package io.micronaut.http.server.limit;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;

/**
 * Creates a {@link RequestLimiter} for each {@link RequestLimiterConfiguration}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Factory
@Internal
final class RequestLimiterFactory {

    @EachBean(RequestLimiterConfiguration.class)
    RequestLimiter requestLimiter(@Parameter RequestLimiterConfiguration configuration) {
        String name = configuration.getName();
        try {
            return switch (configuration.getType()) {
                case CONCURRENCY -> new ConcurrencyRequestLimiter(name, configuration.getMaxConcurrent(), configuration.getRetryAfter());
                case RATE -> new RateRequestLimiter(name, configuration.getPermits(), configuration.getPeriod(), configuration.getBurst());
            };
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid configuration of the request limiter [" + name + "]: " + e.getMessage(), e);
        }
    }
}
