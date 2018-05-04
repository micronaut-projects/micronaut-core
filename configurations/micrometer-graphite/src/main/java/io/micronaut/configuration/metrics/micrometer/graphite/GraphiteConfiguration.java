package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Collect Graphite configuration.
 */
@ConfigurationProperties(GraphiteMeterRegistryFactory.GRAPHITE_CONFIG)
final class GraphiteConfiguration {

    boolean enabled = false;

    String host = "mygraphitehost";

    /**
     * Must parse as valid Duration.
     *
     * @see java.time.Duration#parse(CharSequence)
     */
    String step = "PT1M"; // default 1 minute
}
