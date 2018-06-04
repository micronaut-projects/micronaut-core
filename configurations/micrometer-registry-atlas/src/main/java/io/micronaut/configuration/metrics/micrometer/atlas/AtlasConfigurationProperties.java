package io.micronaut.configuration.metrics.micrometer.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;

import java.util.Optional;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * {@inheritDoc}
 *
 * @see AtlasConfig
 * @author Christian Oestreich
 */
@ConfigurationProperties(MICRONAUT_METRICS_EXPORT)
class AtlasConfigurationProperties implements AtlasConfig {
    private final Properties config;

    public AtlasConfigurationProperties(Environment environment) {
        Optional<Properties> config = environment.getProperty(MICRONAUT_METRICS_EXPORT, Properties.class);
        this.config = config.orElseGet(Properties::new);
    }

    @Override
    public String get(String k) {
        return config.getProperty(NameUtils.hyphenate(k));
    }

}
