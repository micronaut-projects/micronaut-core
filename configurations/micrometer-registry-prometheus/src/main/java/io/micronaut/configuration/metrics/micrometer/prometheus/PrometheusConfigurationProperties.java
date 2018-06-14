package io.micronaut.configuration.metrics.micrometer.prometheus;

import io.micrometer.prometheus.PrometheusConfig;
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
 * @see PrometheusConfig
 */
@ConfigurationProperties(MICRONAUT_METRICS_EXPORT)
class PrometheusConfigurationProperties implements PrometheusConfig {
    private final Properties config;

    /**
     * Constructor for wiring a config which will use environment specific or create new.
     *
     * @param environment Environment
     */
    public PrometheusConfigurationProperties(Environment environment) {
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
