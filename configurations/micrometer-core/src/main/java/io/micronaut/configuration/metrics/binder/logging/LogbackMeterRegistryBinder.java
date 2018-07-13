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

package io.micronaut.configuration.metrics.binder.logging;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Binder factory that will create the logback metrics beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@Requires(classes = LoggerFactory.class)
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".logback.enabled", value = "true", defaultValue = "true")
public class LogbackMeterRegistryBinder {

    /**
     * Logback metrics bean.
     *
     * @return logbackMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    public LogbackMetrics logbackMetrics() {
        return new LogbackMetrics();
    }
}
