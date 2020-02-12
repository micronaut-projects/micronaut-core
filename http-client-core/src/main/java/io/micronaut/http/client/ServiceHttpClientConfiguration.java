/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.context.ClientContextPathProvider;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
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
public class ServiceHttpClientConfiguration extends HttpClientConfiguration implements ClientContextPathProvider {

    /**
     * Prefix for HTTP Client settings.
     */
    public static final String PREFIX = "micronaut.http.services";

    /**
     * The default health check uri.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_HEALTHCHECKURI = "/health";

    /**
     * The default health check value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_HEALTHCHECK = false;

    /**
     * The default health check interval in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_HEALTHCHECKINTERVAL_SECONDS = 30;

    private final String serviceId;
    private final ServiceConnectionPoolConfiguration connectionPoolConfiguration;
    private List<URI> urls = Collections.emptyList();
    private String healthCheckUri = DEFAULT_HEALTHCHECKURI;
    private boolean healthCheck = DEFAULT_HEALTHCHECK;
    private Duration healthCheckInterval = Duration.ofSeconds(DEFAULT_HEALTHCHECKINTERVAL_SECONDS);
    private String path;

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     * @param sslConfiguration The SSL configuration
     * @param applicationConfiguration The application configuration
     */
    public ServiceHttpClientConfiguration(
            @Parameter String serviceId,
            @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
            @Nullable ServiceSslClientConfiguration sslConfiguration,
            ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.serviceId = serviceId;
        if (sslConfiguration != null) {
            setSslConfiguration(sslConfiguration);
        }
        if (connectionPoolConfiguration != null) {
            this.connectionPoolConfiguration = connectionPoolConfiguration;
        } else {
            this.connectionPoolConfiguration = new ServiceConnectionPoolConfiguration();
        }
    }

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     * @param sslConfiguration The SSL configuration
     * @param defaultHttpClientConfiguration The default HTTP client configuration
     */
    @Inject
    public ServiceHttpClientConfiguration(
            @Parameter String serviceId,
            @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
            @Nullable ServiceSslClientConfiguration sslConfiguration,
            HttpClientConfiguration defaultHttpClientConfiguration) {
        super(defaultHttpClientConfiguration);
        this.serviceId = serviceId;
        if (sslConfiguration != null) {
            setSslConfiguration(sslConfiguration);
        }
        if (connectionPoolConfiguration != null) {
            this.connectionPoolConfiguration = connectionPoolConfiguration;
        } else {
            this.connectionPoolConfiguration = new ServiceConnectionPoolConfiguration();
        }
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
     * Sets the health check URI. Default value ({@value #DEFAULT_HEALTHCHECKURI}).
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
     * Sets whether the service health should be checked. Default value ({@value #DEFAULT_HEALTHCHECK}).
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

    @Override
    public Optional<String> getContextPath() {
        return getPath();
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
     * Sets the default duration to check health status. Default value ({@value #DEFAULT_HEALTHCHECKINTERVAL_SECONDS} seconds).
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

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties("ssl")
    public static class ServiceSslClientConfiguration extends SslConfiguration {

        /**
         * Sets the key configuration.
         *
         * @param keyConfiguration The key configuration.
         */
        void setKey(@Nullable DefaultKeyConfiguration keyConfiguration) {
            if (keyConfiguration != null) {
                super.setKey(keyConfiguration);
            }
        }

        /**
         * Sets the key store.
         *
         * @param keyStoreConfiguration The key store configuration
         */
        void setKeyStore(@Nullable DefaultKeyStoreConfiguration keyStoreConfiguration) {
            if (keyStoreConfiguration != null) {
                super.setKeyStore(keyStoreConfiguration);
            }
        }

        /**
         * Sets trust store configuration.
         *
         * @param trustStore The trust store configuration
         */
        void setTrustStore(@Nullable DefaultTrustStoreConfiguration trustStore) {
            if (trustStore != null) {
                super.setTrustStore(trustStore);
            }
        }

        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(SslConfiguration.KeyConfiguration.PREFIX)
        public static class DefaultKeyConfiguration extends SslConfiguration.KeyConfiguration {
        }

        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyStoreConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(SslConfiguration.KeyStoreConfiguration.PREFIX)
        public static class DefaultKeyStoreConfiguration extends SslConfiguration.KeyStoreConfiguration {
        }

        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.TrustStoreConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(SslConfiguration.TrustStoreConfiguration.PREFIX)
        public static class DefaultTrustStoreConfiguration extends SslConfiguration.TrustStoreConfiguration {
        }
    }
}
