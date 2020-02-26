/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.aws;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Default configuration for retrieving Amazon EC2 metadata for {@link io.micronaut.context.env.ComputePlatform#AMAZON_EC2}.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(AmazonMetadataConfiguration.PREFIX)
@Requires(env = Environment.AMAZON_EC2)
public class AmazonMetadataConfiguration implements Toggleable {

    /**
     * Prefix for Amazon EC2 configuration metadata.
     */
    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.AMAZON_EC2 + ".metadata";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default url value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_URL = "http://169.254.169.254/";

    private String url = DEFAULT_URL;
    private String metadataUrl;
    private String instanceDocumentUrl;
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

    /**
     * @return The metadata Url
     */
    public String getMetadataUrl() {
        if (metadataUrl == null) {
            return url + "/latest/meta-data/";
        }
        return metadataUrl;
    }

    /**
     * @param metadataUrl The metadata Url
     */
    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    /**
     * @return The instance document Url
     */
    public String getInstanceDocumentUrl() {
        if (instanceDocumentUrl == null) {
            return url + "/latest/dynamic/instance-identity/document";
        }
        return instanceDocumentUrl;
    }

    /**
     * @param instanceDocumentUrl The instance document Url
     */
    public void setInstanceDocumentUrl(String instanceDocumentUrl) {
        this.instanceDocumentUrl = instanceDocumentUrl;
    }
}
