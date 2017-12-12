/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.health;

import org.particleframework.context.annotation.Requires;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Read;
import org.particleframework.management.endpoint.health.aggregator.HealthAggregator;
import org.particleframework.management.endpoint.health.indicator.HealthIndicator;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the health of the application.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint("health")
@Requires(endpoint = "endpoints.health")
public class HealthEndpoint {

    private HealthAggregator healthAggregator;
    private HealthIndicator[] healthIndicators;

    public HealthEndpoint(HealthAggregator healthAggregator, HealthIndicator[] healthIndicators) {
        this.healthAggregator = healthAggregator;
        this.healthIndicators = healthIndicators;
    }

    @Read
    Object getHealth() {
        return healthAggregator.aggregate(healthIndicators);
    }
}
