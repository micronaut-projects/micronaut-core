/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.cloud.gcp;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.Environment;
import org.particleframework.core.util.Toggleable;
import org.particleframework.runtime.ApplicationConfiguration;

import java.time.Duration;

/**
 * Configuration for computing metadata for {@link org.particleframework.context.env.ComputePlatform#GOOGLE_COMPUTE}
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GoogleComputeMetadataConfiguration.PREFIX)
@Requires(env = Environment.GOOGLE_COMPUTE)
public class GoogleComputeMetadataConfiguration implements Toggleable {
    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.GOOGLE_COMPUTE + ".metadata";
    private boolean enabled = true;
    private String metadataUrl = "http://metadata.google.internal/computeMetadata/v1/project/";
    private String projectMetadataUrl = "http://metadata.google.internal/project/v1/project/";
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration connectTimeout = Duration.ofSeconds(2);

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMetadataUrl() {
        return metadataUrl;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public String getProjectMetadataUrl() {
        return projectMetadataUrl;
    }

    public void setProjectMetadataUrl(String projectMetadataUrl) {
        this.projectMetadataUrl = projectMetadataUrl;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
