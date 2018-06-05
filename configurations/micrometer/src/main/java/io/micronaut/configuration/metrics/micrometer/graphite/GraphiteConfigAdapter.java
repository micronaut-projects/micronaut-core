package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteProtocol;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Overrides the GraphiteConfig defaults with the values from GraphiteConfigurationProperties.
 */
public class GraphiteConfigAdapter implements GraphiteConfig {

    GraphiteConfigurationProperties props;

    /**
     *
     * @param props Config from the application
     */
    public GraphiteConfigAdapter(GraphiteConfigurationProperties props) {
        this.props = props;
    }

    @Override
    public boolean enabled() {
        return propertyOverride(props.isEnabled(), GraphiteConfig.super.enabled());
    }

    @Override
    public Duration step() {
        return propertyOverride(props.getStep(), GraphiteConfig.super.step());
    }

    @Override
    public TimeUnit rateUnits() {
        return propertyOverride(props.getRateUnits(), GraphiteConfig.super.rateUnits());
    }

    @Override
    public TimeUnit durationUnits() {
        return propertyOverride(props.getDurationUnits(), GraphiteConfig.super.durationUnits());
    }

    @Override
    public String host() {
        return propertyOverride(props.getHost(), GraphiteConfig.super.host());
    }

    @Override
    public int port() {
        return propertyOverride(props.getPort(), GraphiteConfig.super.port());
    }

    @Override
    public GraphiteProtocol protocol() {
        return propertyOverride(props.getProtocol(), GraphiteConfig.super.protocol());
    }

    @Override
    public String[] tagsAsPrefix() {
        return propertyOverride(props.getTagsAsPrefix(), GraphiteConfig.super.tagsAsPrefix());
    }

    @Override
    public String get(String key) {
        return null;
    }

    /**
     * Override configVal with propVal if present.
     * @param propVal The property value
     * @param configVal The GraphiteConfig val
     * @param <V> The type returned is the same as the type passed in
     * @return The value to use in configuring Graphite
     */
    final <V> V propertyOverride(V propVal, V configVal) {
        return propVal != null ? propVal : configVal;
    }
}
