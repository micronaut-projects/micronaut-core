package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.graphite.GraphiteProtocol;
import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Collect Graphite configuration.
 */
@ConfigurationProperties(GraphiteMeterRegistryFactory.GRAPHITE_CONFIG)
class GraphiteConfigurationProperties {

    private TimeUnit durationUnits = TimeUnit.MILLISECONDS;

    private boolean enabled = true;

    private String host = "localhost";

    private Integer port = 2004;

    private String prefix = "micronaut";

    private GraphiteProtocol protocol =  GraphiteProtocol.PICKLED;

    private TimeUnit rateUnits = TimeUnit.SECONDS;

    private Duration step = Duration.ofMinutes(1);

    private String[] tagsAsPrefix = new String[0];

    /**
     *
     * @return durationUnits
     */
    public TimeUnit getDurationUnits() {
        return durationUnits;
    }

    /**
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     *
     * @return The Graphite server host
     */
    public String getHost() {
        return host;
    }

    /**
     *
     * @return The Graphite server port.
     */
    public Integer getPort() {
        return port;
    }

    /**
     *
     * @return The metrics prefix to be used.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     *
     * @return The Protocol; i.e. PLAINTEXT, UDP, PICKLED
     */
    public GraphiteProtocol getProtocol() {
        return protocol;
    }

    /**
     *
     * @return The rate units
     */
    public TimeUnit getRateUnits() {
        return rateUnits;
    }

    /**
     *
     * @return The duration step
     */
    public Duration getStep() {
        return step;
    }

    /**
     *
     * @return Tags to be used as the prefix
     */
    public String[] getTagsAsPrefix() {
        return tagsAsPrefix;
    }

    /**
     *
     * @param durationUnits The duration units
     */
    public void setDurationUnits(TimeUnit durationUnits) {
        this.durationUnits = durationUnits;
    }

    /**
     *
     * @param enabled If Graphite metrics are enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     *
     * @param host The Graphite server host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     *
     * @param port The Graphite server port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     *
     * @param prefix The metrics prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     *
     * @param protocol The protocol
     */
    public void setProtocol(GraphiteProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     *
     * @param rateUnits The rateUnits
     */
    public void setRateUnits(TimeUnit rateUnits) {
        this.rateUnits = rateUnits;
    }

    /**
     *
     * @param step The step
     */
    public void setStep(Duration step) {
        this.step = step;
    }

    /**
     *
     * @param tagsAsPrefix The tagsAsPrefix
     */
    public void setTagsAsPrefix(String[] tagsAsPrefix) {
        this.tagsAsPrefix = tagsAsPrefix;
    }
}
