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

package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.Collection;

/**
 * Default implementation of {@link MeterRegistryConfigurer} that adds the binders and filters
 * to the micrometer meter registry.  This is specifically needed for the {@link io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint}
 *
 * @author Christian Oestreich
 * @since 1.0
 */
public class MicrometerMeterRegistryConfigurer implements MeterRegistryConfigurer {

    /**
     * Constructor for the micrometer meter registry configurer.
     * @param meterRegistry the meter registry bean
     * @param binders list of binder beans
     * @param filters list of filter beans
     */
    public MicrometerMeterRegistryConfigurer(
            MeterRegistry meterRegistry,
            Collection<MeterBinder> binders,
            Collection<MeterFilter> filters) {
        addFilters(meterRegistry, filters);
        addBinders(meterRegistry, binders);
    }

    /**
     * Add filters to the meter registry. More details available at https://micrometer.io/docs/concepts#_meter_filters
     *
     * @param registry the meter registry
     * @param filters  list of the current filters
     */
    private void addFilters(MeterRegistry registry, Collection<MeterFilter> filters) {
        if (filters != null && !filters.isEmpty()) {
            filters.forEach(registry.config()::meterFilter);
        }
    }

    /**
     * Add binders to the meter registry.  There are default binders available.
     * <p>
     * {@link io.micronaut.configuration.metrics.binder.jvm.JvmMeterRegistryBinder}
     * {@link io.micronaut.configuration.metrics.binder.logging.LogbackMeterRegistryBinder}
     * {@link io.micronaut.configuration.metrics.binder.system.SystemMeterRegistryBinder}
     *
     * @param registry the meter registry
     * @param binders  list of the current binders
     */
    private void addBinders(MeterRegistry registry, final Collection<MeterBinder> binders) {
        if (binders != null && !binders.isEmpty()) {
            binders.forEach((binder) -> binder.bindTo(registry));
        }
    }
}
