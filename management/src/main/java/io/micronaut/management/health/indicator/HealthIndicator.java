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

import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

/**
 * <p>Describes an indicator of health of the application. Used by the
 * {@link io.micronaut.management.health.aggregator.HealthAggregator} to create a response combining all indicators.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface HealthIndicator extends Ordered {

    /**
     * @return A publisher that returns a {@link HealthResult} that provides the
     * information necessary to build a response.
     */
    Publisher<HealthResult> getResult();
}
