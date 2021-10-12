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
package io.micronaut.management.health.aggregator;

import io.micronaut.management.endpoint.health.HealthLevelOfDetail;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

/**
 * <p>Aggregates all registered health indicators into a single response.</p>
 *
 * @param <T> The aggregator type
 * @author James Kleeh
 * @since 1.0
 */
public interface HealthAggregator<T extends HealthResult> {

    /**
     * @param indicators The health indicators to aggregate.
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return An aggregated response.
     */
    Publisher<T> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail);

    /**
     * @param name    The name of the new health result
     * @param results The health results to aggregate.
     * @return An aggregated {@link HealthResult}.
     */
    Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results);
}
