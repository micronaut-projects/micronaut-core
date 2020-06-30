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
package io.micronaut.discovery.cloud.oraclecloud;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Default configuration for retrieving Oracle Cloud metadata for {@link io.micronaut.context.env.ComputePlatform#ORACLE_CLOUD}.
 *
 * @author Todd Sharp
 * @since 1.2.0
 */
@ConfigurationProperties(OracleCloudMetadataConfiguration.PREFIX)
@Requires(env = Environment.ORACLE_CLOUD)
public class OracleCloudMetadataConfiguration implements Toggleable {

    /**
     * Prefix for Oracle Cloud configuration metadata.
     */
    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.ORACLE_CLOUD + ".metadata";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default url value.
     */
    @SuppressWarnings("WeakerAccess")
    // CHECKSTYLE:OFF
    public static final String DEFAULT_URL = "http://169.254.169.254/opc/v1/instance/";
    public static final String DEFAULT_VNIC_URL = "http://169.254.169.254/opc/v1/vnics/";
    // CHECKSTYLE:ON

    private String url = DEFAULT_URL;
    private String metadataUrl;
    private String instanceDocumentUrl;
    private String vnicUrl = DEFAULT_VNIC_URL;
    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the Oracle Cloud configuration is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable or disable the Oracle Cloud configuration
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
        return url;
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
        return url;
    }

    /**
     * @param instanceDocumentUrl The instance document Url
     */
    public void setInstanceDocumentUrl(String instanceDocumentUrl) {
        this.instanceDocumentUrl = instanceDocumentUrl;
    }

    /**
     * * Default value ({@value #DEFAULT_VNIC_URL}).
     * @return The VNIC Url
     */
    public String getVnicUrl() {
        if (vnicUrl == null) {
            return DEFAULT_VNIC_URL;
        }
        return vnicUrl;
    }

    /**
     * @param vnicUrl The instance document Url
     */
    public void setVnicUrl(String vnicUrl) {
        this.vnicUrl = vnicUrl;
    }
}
