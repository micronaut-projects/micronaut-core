package io.micronaut.discovery.cloud.digitalocean;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

@ConfigurationProperties(DigitalOceanMetadataConfiguration.PREFIX)
@Requires(env = Environment.DIGITAL_OCEAN)
public class DigitalOceanMetadataConfiguration implements Toggleable {


    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.DIGITAL_OCEAN + ".metadata";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default url value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_URL = "http://169.254.169.254/metadata/v1.json";

    private String url = DEFAULT_URL;
    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the Amazon EC2 configuration is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable or disable the Amazon EC2 configuration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The Url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Default value ({@value #DEFAULT_URL}).
     * @param url The url
     */
    public void setUrl(String url) {
        this.url = url;
    }


}
