package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;

/**
 * Graphite properties interface
 */
interface GraphiteConfiguration {

    String GRAPHITE_CONFIG = MeterRegistryFactory.CFG_ROOT + "export.graphite";
    String GRAPHITE_ENABLED = GRAPHITE_CONFIG + ".enabled";
    String GRAPHITE_HOST = GRAPHITE_CONFIG + ".host";
    String GRAPHITE_STEP = GRAPHITE_CONFIG + ".step";
    String GRAPHITE_PORT = GRAPHITE_CONFIG + ".port";

    boolean isEnabled();

    String getHost();

    String getStep();

    int getPort();
}
