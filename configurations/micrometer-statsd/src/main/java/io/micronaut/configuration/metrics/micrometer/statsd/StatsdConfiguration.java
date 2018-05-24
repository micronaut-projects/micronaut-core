package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.statsd.StatsdFlavor;
import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;

/**
 * Statsd properties interface.
 */
public interface StatsdConfiguration {

    String STATSD_CONFIG = MeterRegistryFactory.CFG_ROOT + "export.statsd";
    String STATSD_ENABLED = STATSD_CONFIG + ".enabled";
    String STATSD_FLAVOR = STATSD_CONFIG + ".flavor";
    String STATSD_PORT = STATSD_CONFIG + ".port";
    String STATSD_HOST = STATSD_CONFIG + ".host";
    String STATSD_STEP = STATSD_CONFIG + ".step";

    /**
     * The flavor of metrics, defaults to DATADOG.
     * @return statsd flavor
     */
    StatsdFlavor getFlavor();

    /**
     * Flag for whether statsd metrics reporter is enabled.
     * @return enabled flag
     */
    boolean isEnabled();

    /**
     * Name of the statsd host, defaults to "localhost".
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
     * Port of the statsd host, defaults to "8125".
     * @return port number
     */
    int getPort();
}
