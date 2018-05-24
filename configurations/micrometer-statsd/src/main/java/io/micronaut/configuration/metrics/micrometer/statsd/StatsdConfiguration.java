package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.statsd.StatsdFlavor;
import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;

public interface StatsdConfiguration {

    String STATSD_CONFIG = MeterRegistryFactory.CFG_ROOT + "export.statsd";
    String STATSD_ENABLED = STATSD_CONFIG + ".enabled";
    String STATSD_FLAVOR = STATSD_CONFIG + ".flavor";
    String STATSD_PORT = STATSD_CONFIG + ".port";
    String STATSD_HOST = STATSD_CONFIG + ".host";
    String STATSD_STEP = STATSD_CONFIG + ".step";

    StatsdFlavor getFlavor();

    boolean isEnabled();

    int getPort();

    String getHost();

    String getStep();
}
