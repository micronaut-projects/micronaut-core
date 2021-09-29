package io.micronaut.management.endpoint;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(EndpointsFilterConfigurationProperties.PREFIX)
public class EndpointsFilterConfigurationProperties implements EndpointsFilterConfiguration {
    /**
     * The prefix for the {@link EndpointsFilter} configuration
     */
    public static final String PREFIX = "endpoints.filter";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the {@link EndpointsFilter} is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable or disable the {@link EndpointsFilter}
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
