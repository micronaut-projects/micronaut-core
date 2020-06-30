/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.digitalocean;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Default configuration for retrieving Digital Ocean metadata for {@link io.micronaut.context.env.ComputePlatform#DIGITAL_OCEAN}.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
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
