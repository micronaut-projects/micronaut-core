/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.health.indicator;


/**
 * Options for {@link io.micronaut.management.endpoint.health.HealthEndpoint} selector that aggregates
 * {@link io.micronaut.management.health.indicator.HealthIndicator} according to {@link io.micronaut.management.health.indicator.annotation.Liveness}
 * respectively {@link io.micronaut.management.health.indicator.annotation.Readiness} qualifiers.
 *
 * @author Pavol Gressa
 * @since 2.1
 */
public enum HealthCheckType {

    /**
     * Liveness health indicators.
     */
    LIVENESS,

    /**
     * Readiness health indicators.
     */
    READINESS,
}
