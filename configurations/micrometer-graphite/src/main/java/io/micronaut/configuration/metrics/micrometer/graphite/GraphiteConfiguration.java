package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;

/**
 * Graphite properties interface.
 */
interface GraphiteConfiguration {

    String GRAPHITE_CONFIG = MeterRegistryFactory.MICRONAUT_METRICS + "export.graphite";
    String GRAPHITE_ENABLED = GRAPHITE_CONFIG + ".enabled";
    String GRAPHITE_HOST = GRAPHITE_CONFIG + ".host";
    String GRAPHITE_STEP = GRAPHITE_CONFIG + ".step";
    String GRAPHITE_PORT = GRAPHITE_CONFIG + ".port";

    /**
     * Flag for whether graphite metrics reporter is enabled.
     * @return enabled flag
     */
    boolean isEnabled();

    /**
     * Name of the graphite host, defaults to "localhost".
     * @return host name
     */
    String getHost();

    /**
     * Duration to report metrics, defaults to "PT1M" (1min).
     *
     * @see java.time.Duration#parse(CharSequence)
     * @return parsable duration string
     */
    String getStep();

    /**
     * Port of the graphite host, defaults to "2004".
     * @return port number
     */
    int getPort();
}
