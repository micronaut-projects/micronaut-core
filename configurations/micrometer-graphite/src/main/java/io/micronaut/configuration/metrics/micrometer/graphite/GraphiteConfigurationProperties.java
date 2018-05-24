package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Graphite properties implementation.
 */
@ConfigurationProperties(GraphiteConfiguration.GRAPHITE_CONFIG)
class GraphiteConfigurationProperties implements GraphiteConfiguration {

    private boolean enabled = true;

    private String host = "localhost";

    /**
     * Must parse as valid Duration.
     *
     * @see java.time.Duration#parse(CharSequence)
     */
    private String step = "PT1M"; // default 1 minute

    private int port = 2004;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set enabled flag.
     *
     * @param enabled enable graphite metrics
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getHost() {
        return host;
    }

    /**
     * Set the hostname.
     *
     * @param host hostname
     */
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public String getStep() {
        return step;
    }

    /**
     * Set the step interval for reporting.
     *
     * @see java.time.Duration#parse(CharSequence)
     * @param step Parsable step string
     */
    public void setStep(String step) {
        this.step = step;
    }

    @Override
    public int getPort() {
        return port;
    }

    /**
     * Port to communicate with graphite.
     *
     * @param port port number
     */
    public void setPort(int port) {
        this.port = port;
    }
}
