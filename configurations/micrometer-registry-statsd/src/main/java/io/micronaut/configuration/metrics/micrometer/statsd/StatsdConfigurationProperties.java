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

package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.statsd.StatsdConfig;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;

import java.util.Optional;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * {@inheritDoc}.
 *
 * @author Christian Oestreich
 * @see StatsdConfig
 */
@ConfigurationProperties(MICRONAUT_METRICS_EXPORT)
class StatsdConfigurationProperties implements StatsdConfig {

    private final Properties config;

    /**
     * Constructor for wiring a config which will use environment specific or create new.
     *
     * @param environment Environment
     */
    public StatsdConfigurationProperties(Environment environment) {
        Optional<Properties> config = environment.getProperty(MICRONAUT_METRICS_EXPORT, Properties.class);
        this.config = config.orElseGet(Properties::new);
    }

    /**
     * Method to get config param.  Will hyphenate the properties.
     *
     * @param k key
     * @return String value of property
     */
    @Override
    public String get(String k) {
        return config.getProperty(NameUtils.hyphenate(k));
    }
}
