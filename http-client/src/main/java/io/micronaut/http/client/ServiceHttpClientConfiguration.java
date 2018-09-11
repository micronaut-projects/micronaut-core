/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.client;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.CollectionUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.client.ServiceHttpClientConfiguration.PREFIX;

/**
 * Allows defining HTTP client configurations via the {@code micronaut.http.services} setting.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(PREFIX)
public class ServiceHttpClientConfiguration extends HttpClientConfiguration {

    /**
     * Prefix for HTTP Client settings.
     */
    public static final String PREFIX = "micronaut.http.services";

    private final String serviceId;
    private final ServiceConnectionPoolConfiguration connectionPoolConfiguration;
    private List<URI> urls = Collections.emptyList();
    private String healthCheckUri = "/health";
    private boolean healthCheck = false;
    private Duration healthCheckInterval = Duration.ofSeconds(30);
    private String path;

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     */
    public ServiceHttpClientConfiguration(@Parameter String serviceId, ServiceConnectionPoolConfiguration connectionPoolConfiguration) {
        this.serviceId = serviceId;
        this.connectionPoolConfiguration = connectionPoolConfiguration;
    }

    /**
     * The service id.
     *
     * @return The ID of the service
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * The URLs.
     *
     * @return The URLs of the service
     */
    public List<URI> getUrls() {
        return urls;
    }

    /**
     * Sets the URIs of the service.
     *
     * @param urls The URIs
     */
    public void setUrls(List<URI> urls) {
        if (CollectionUtils.isNotEmpty(urls)) {
            this.urls = urls;
        }
    }

    /**
     * Sets the URL of the service.
     *
     * @param url The URI
     */
    public void setUrl(URI url) {
        if (url != null) {
            this.urls = Collections.singletonList(url);
        }
    }

    /**
     * The URI to check the service for health status.
     *
     * @return The health status uri
     */
    public String getHealthCheckUri() {
        return healthCheckUri;
    }

    /**
     * Sets the health check URI.
     *
     * @param healthCheckUri The health check URI
     */
    public void setHealthCheckUri(String healthCheckUri) {
        this.healthCheckUri = healthCheckUri;
    }

    /**
     * Whether the service health should be checked.
     *
     * @return True if the health should be checked
     */
    public boolean isHealthCheck() {
        return healthCheck;
    }

    /**
     * Sets whether the service health should be checked.
     * @param healthCheck True if the health should be checked
     */
    public void setHealthCheck(boolean healthCheck) {
        this.healthCheck = healthCheck;
    }

    /**
     * The context path to use for requests.
     *
     * @return The context path
     */
    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    /**
     * Sets the context path to use for requests.
     *
     * @param path The context path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * The default duration to check health status.
     *
     * @return The duration
     */
    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    /**
     * Sets the default duration to check health status.
     *
     * @param healthCheckInterval The duration
     */
    public void setHealthCheckInterval(Duration healthCheckInterval) {
        if (healthCheckInterval != null) {
            this.healthCheckInterval = healthCheckInterval;
        }
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return connectionPoolConfiguration;
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    public static class ServiceConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

}
