/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.management.endpoint.health;

import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.Read;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.reactivex.Single;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the health of the application.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint(HealthEndpoint.NAME)
public class HealthEndpoint {

    /**
     * Constant for health.
     */
    public static final String NAME = "health";

    /**
     * Prefix for health endpoint.
     */
    public static final String PREFIX = EndpointConfiguration.PREFIX + "." + NAME;

    private HealthAggregator healthAggregator;
    private HealthIndicator[] healthIndicators;

    /**
     * @param healthAggregator The {@link HealthAggregator}
     * @param healthIndicators The {@link HealthIndicator}
     */
    public HealthEndpoint(HealthAggregator healthAggregator, HealthIndicator[] healthIndicators) {
        this.healthAggregator = healthAggregator;
        this.healthIndicators = healthIndicators;
    }

    /**
     * @return The health information as a {@link Single}
     */
    @Read
    Single getHealth() {
        return Single.fromPublisher(healthAggregator.aggregate(healthIndicators));
    }
}
