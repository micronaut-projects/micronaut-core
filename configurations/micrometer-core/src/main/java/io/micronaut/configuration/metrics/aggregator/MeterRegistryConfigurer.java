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

/**
 * Class that will configure meter registries.  This is done on bean added event so that
 * composite registry can be skipped and non-composite registries can be added to composite.
 *
 * @author Christian Oestreich
 * @param <T> an instance of a meter registry that will be configured
 * @since 1.0
 */
public interface MeterRegistryConfigurer<T extends MeterRegistry> {

    /**
     * Method to configure a meter registry with binders, filters, etc.
     *
     * @param meterRegistry Meter Registry
     */
    void configure(T meterRegistry);

    /**
     * Method to determine if this configurer supports the meter registry type.
     *
     * @param meterRegistry a meter registry
     * @return boolean whether is supported
     */
    boolean supports(T meterRegistry);
}
