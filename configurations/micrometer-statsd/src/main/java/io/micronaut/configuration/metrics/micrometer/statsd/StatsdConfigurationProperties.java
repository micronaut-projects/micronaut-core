package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.statsd.StatsdFlavor;
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Collect Statsd configuration.
 */
@ConfigurationProperties(StatsdConfiguration.STATSD_CONFIG)
class StatsdConfigurationProperties implements StatsdConfiguration {

    private StatsdFlavor flavor = StatsdFlavor.DATADOG;

    private boolean enabled = true;

    private int port = 8125;

    private String host = "localhost";

    /**
     * Must parse as valid Duration.
     *
     * @see java.time.Duration#parse(CharSequence)
     */
    private String step = "PT1M"; // default 1 minute

    @Override
    public StatsdFlavor getFlavor() {
        return flavor;
    }

    public void setFlavor(StatsdFlavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }
}
