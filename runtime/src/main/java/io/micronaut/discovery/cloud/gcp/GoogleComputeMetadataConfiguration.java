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
package io.micronaut.discovery.cloud.gcp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

import java.time.Duration;

/**
 * Configuration for computing metadata for {@link io.micronaut.context.env.ComputePlatform#GOOGLE_COMPUTE}.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GoogleComputeMetadataConfiguration.PREFIX)
@Requires(env = Environment.GOOGLE_COMPUTE)
public class GoogleComputeMetadataConfiguration implements Toggleable {

    /**
     * Prefix for Google Compute configuration.
     */
    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.GOOGLE_COMPUTE + ".metadata";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default metadata url value.
     */
    @SuppressWarnings("WeakerAccess")
    // CHECKSTYLE:OFF
    public static final String DEFAULT_METADATAURL = "http://metadata.google.internal/computeMetadata/v1/project/";
    // CHECKSTYLE:ON

    /**
     * The default project metadata url value.
     */
    @SuppressWarnings("WeakerAccess")
    // CHECKSTYLE:OFF
    public static final String DEFAULT_PROJECTMETADATAURL = "http://metadata.google.internal/project/v1/project/";
    // CHECKSTYLE:ON

    /**
     * The default read timeout in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_READTIMEOUT = 5;

    /**
     * The default connect timeout in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_CONNECTTIMEOUT = 2;

    private boolean enabled = DEFAULT_ENABLED;
    private String metadataUrl = DEFAULT_METADATAURL;
    private String projectMetadataUrl = DEFAULT_PROJECTMETADATAURL;
    private Duration readTimeout = Duration.ofSeconds(DEFAULT_READTIMEOUT);
    private Duration connectTimeout = Duration.ofSeconds(DEFAULT_CONNECTTIMEOUT);

    /**
     * @return Whether the Google Compute configuration is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable or disable the Google Compute configuration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The metadata Url
     */
    public String getMetadataUrl() {
        return metadataUrl;
    }

    /**
     * Default value ({@value #DEFAULT_METADATAURL}).
     * @param metadataUrl The metadata Url
     */
    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    /**
     * @return The project metadata Url
     */
    public String getProjectMetadataUrl() {
        return projectMetadataUrl;
    }

    /**
     * Default value ({@value #DEFAULT_PROJECTMETADATAURL}).
     * @param projectMetadataUrl The project metadata Url
     */
    public void setProjectMetadataUrl(String projectMetadataUrl) {
        this.projectMetadataUrl = projectMetadataUrl;
    }

    /**
     * @return The read timeout
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * Default value ({@value #DEFAULT_READTIMEOUT} seconds).
     * @param readTimeout The read timeout
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * @return The connect timeout
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Default value ({@value #DEFAULT_CONNECTTIMEOUT}).
     * @param connectTimeout The connect timeout
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
