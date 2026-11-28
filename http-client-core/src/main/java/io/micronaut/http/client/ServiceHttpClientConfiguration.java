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
package io.micronaut.http.client;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.context.ClientContextPathProvider;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.client.internal.ExchangeRetry;
import io.micronaut.http.client.internal.PolicyExchangeRetry;
import io.micronaut.http.client.loadbalance.OutlierDetectionConfiguration;
import io.micronaut.retry.RetryRegistry;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
    private final ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration;
    private final ServiceHttp2ClientConfiguration http2Configuration;
    private final ServiceOutlierDetectionConfiguration outlierDetection;
    private List<URI> urls = Collections.emptyList();
    private String healthCheckUri = DEFAULT_HEALTHCHECKURI;
    private boolean healthCheck = DEFAULT_HEALTHCHECK;
    private Duration healthCheckInterval = Duration.ofSeconds(DEFAULT_HEALTHCHECKINTERVAL_SECONDS);
    private HealthCheckAllUnhealthy healthCheckAllUnhealthy = HealthCheckAllUnhealthy.USE_ALL;
    @Nullable
    private String path;
    @Nullable
    private String loadBalancerStrategy;
    @Nullable
    private String loadBalancerKeyHeader;
    private ServiceExchangeRetryConfiguration retryConfiguration = new ServiceExchangeRetryConfiguration(null, null, null);

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
        this.webSocketCompressionConfiguration = new ServiceWebSocketCompressionConfiguration();
        this.http2Configuration = new ServiceHttp2ClientConfiguration();
        this.outlierDetection = new ServiceOutlierDetectionConfiguration();
    }

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     * @param sslConfiguration The SSL configuration
     * @param defaultHttpClientConfiguration The default HTTP client configuration
     * @deprecated Use {@link #ServiceHttpClientConfiguration(String, ServiceConnectionPoolConfiguration, ServiceWebSocketCompressionConfiguration, ServiceHttp2ClientConfiguration, ServiceSslClientConfiguration, HttpClientConfiguration)} instead.
     */
    @Deprecated(since = "4.3.0")
    public ServiceHttpClientConfiguration(
            @Parameter String serviceId,
            @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
            @Nullable ServiceSslClientConfiguration sslConfiguration,
            HttpClientConfiguration defaultHttpClientConfiguration) {
        this(serviceId, connectionPoolConfiguration, new ServiceWebSocketCompressionConfiguration(), sslConfiguration, defaultHttpClientConfiguration);
    }

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     * @param webSocketCompressionConfiguration The WebSocket compression configuration
     * @param sslConfiguration The SSL configuration
     * @param defaultHttpClientConfiguration The default HTTP client configuration
     * @deprecated Use {@link #ServiceHttpClientConfiguration(String, ServiceConnectionPoolConfiguration, ServiceWebSocketCompressionConfiguration, ServiceHttp2ClientConfiguration, ServiceSslClientConfiguration, HttpClientConfiguration)} instead.
     */
    @Deprecated(since = "4.6.0")
    public ServiceHttpClientConfiguration(
        @Parameter String serviceId,
        @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
        @Nullable ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration,
        @Nullable ServiceSslClientConfiguration sslConfiguration,
        HttpClientConfiguration defaultHttpClientConfiguration) {
        this(serviceId, connectionPoolConfiguration, webSocketCompressionConfiguration, null, sslConfiguration, defaultHttpClientConfiguration);
    }

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId The service id
     * @param connectionPoolConfiguration The connection pool configuration
     * @param webSocketCompressionConfiguration The WebSocket compression configuration
     * @param http2Configuration The HTTP/2 configuration
     * @param sslConfiguration The SSL configuration
     * @param defaultHttpClientConfiguration The default HTTP client configuration
     */
    @Deprecated(since = "5.3.0")
    public ServiceHttpClientConfiguration(
            @Parameter String serviceId,
            @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
            @Nullable ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration,
            ServiceHttpClientConfiguration.@Nullable ServiceHttp2ClientConfiguration http2Configuration,
            @Nullable ServiceSslClientConfiguration sslConfiguration,
            HttpClientConfiguration defaultHttpClientConfiguration) {
        this(serviceId, connectionPoolConfiguration, webSocketCompressionConfiguration, http2Configuration, sslConfiguration, null, defaultHttpClientConfiguration);
    }

    /**
     * Creates a new client configuration for the given service ID.
     *
     * @param serviceId                         The service id
     * @param connectionPoolConfiguration       The connection pool configuration
     * @param webSocketCompressionConfiguration The WebSocket compression configuration
     * @param http2Configuration                The HTTP/2 configuration
     * @param sslConfiguration                  The SSL configuration
     * @param outlierDetection                  The outlier detection configuration
     * @param defaultHttpClientConfiguration    The default HTTP client configuration
     * @since 5.3.0
     */
    @Inject
    public ServiceHttpClientConfiguration(
            @Parameter String serviceId,
            @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
            @Nullable ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration,
            ServiceHttpClientConfiguration.@Nullable ServiceHttp2ClientConfiguration http2Configuration,
            @Nullable ServiceSslClientConfiguration sslConfiguration,
            @Nullable ServiceOutlierDetectionConfiguration outlierDetection,
            HttpClientConfiguration defaultHttpClientConfiguration) {
        super(defaultHttpClientConfiguration);
        this.outlierDetection = outlierDetection != null ? outlierDetection : new ServiceOutlierDetectionConfiguration();
        this.serviceId = serviceId;
        if (sslConfiguration != null) {
            setSslConfiguration(sslConfiguration);
        }
        this.connectionPoolConfiguration = connectionPoolConfiguration == null ?
                new ServiceConnectionPoolConfiguration(defaultHttpClientConfiguration) : connectionPoolConfiguration;
        this.webSocketCompressionConfiguration = Objects.requireNonNullElseGet(webSocketCompressionConfiguration, ServiceWebSocketCompressionConfiguration::new);
        this.http2Configuration = http2Configuration == null ?
                new ServiceHttp2ClientConfiguration(defaultHttpClientConfiguration) : http2Configuration;
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
     * Whether the service health should be checked: the load balancer of the service probes
     * the {@link #getHealthCheckUri() health check URI} of each of its instances, configured
     * with {@code urls} or found by a discovery client, and leaves out of the selection an
     * instance that fails, unless every instance does.
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

    /**
     * Uses the TLS configuration of the connection to the HTTP proxy of this service, when
     * configured. Otherwise the one of the default client applies.
     *
     * @param proxySslConfiguration The provider of the proxy TLS configuration, present when configured
     * @since 5.3.0
     */
    @Inject
    public void setServiceProxySslConfiguration(BeanProvider<ServiceProxySslClientConfiguration> proxySslConfiguration) {
        proxySslConfiguration.ifPresent(this::setProxySslConfiguration);
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return connectionPoolConfiguration;
    }

    @Override
    public WebSocketCompressionConfiguration getWebSocketCompressionConfiguration() {
        return webSocketCompressionConfiguration;
    }

    @Override
    public ServiceHttp2ClientConfiguration getHttp2Configuration() {
        return http2Configuration;
    }

    /**
     * What the load balancer of the service does when the health check finds every instance
     * unhealthy: use all of them anyway ({@link HealthCheckAllUnhealthy#USE_ALL}, the default),
     * or none ({@link HealthCheckAllUnhealthy#NONE}), so that requests fail at once with no
     * available instance.
     *
     * @return The policy
     * @since 5.3.0
     */
    public HealthCheckAllUnhealthy getHealthCheckAllUnhealthy() {
        return healthCheckAllUnhealthy;
    }

    /**
     * Sets what the load balancer does when every instance is unhealthy. Default value
     * {@link HealthCheckAllUnhealthy#USE_ALL}.
     *
     * @param healthCheckAllUnhealthy The policy, or {@code null} to keep the current one
     * @since 5.3.0
     */
    public void setHealthCheckAllUnhealthy(@Nullable HealthCheckAllUnhealthy healthCheckAllUnhealthy) {
        if (healthCheckAllUnhealthy != null) {
            this.healthCheckAllUnhealthy = healthCheckAllUnhealthy;
        }
    }

    /**
     * The outlier detection of the service: whether an instance that keeps failing stops being
     * selected for a while.
     *
     * @return The outlier detection configuration
     * @since 5.3.0
     */
    public OutlierDetectionConfiguration getOutlierDetection() {
        return outlierDetection;
    }

    /**
     * How the load balancer of the service picks among its available instances: a built-in
     * {@link io.micronaut.http.client.loadbalance.LoadBalancerStrategy strategy}
     * ({@code round-robin}, {@code random}, {@code p2c}, {@code weighted}, {@code sticky}) or the
     * name of a strategy bean. Defaults to {@code null}, round robin.
     *
     * @return The name of the strategy, or {@code null}
     * @since 5.3.0
     */
    public @Nullable String getLoadBalancerStrategy() {
        return loadBalancerStrategy;
    }

    /**
     * @param loadBalancerStrategy See {@link #getLoadBalancerStrategy()}
     * @since 5.3.0
     */
    public void setLoadBalancerStrategy(@Nullable String loadBalancerStrategy) {
        this.loadBalancerStrategy = loadBalancerStrategy;
    }

    /**
     * The request header that holds the
     * {@link io.micronaut.http.client.loadbalance.LoadBalancerKey key} of a request for the
     * load balancer, e.g. a session id for the {@code sticky} strategy, when the request has no
     * key attribute. Defaults to {@code null}, none.
     *
     * @return The name of the header, or {@code null}
     * @since 5.3.0
     */
    @Experimental
    public @Nullable String getLoadBalancerKeyHeader() {
        return loadBalancerKeyHeader;
    }

    /**
     * @param loadBalancerKeyHeader See {@link #getLoadBalancerKeyHeader()}
     * @since 5.3.0
     */
    @Experimental
    public void setLoadBalancerKeyHeader(@Nullable String loadBalancerKeyHeader) {
        this.loadBalancerKeyHeader = loadBalancerKeyHeader;
    }

    /**
     * The retry of the exchanges of the service, decided from the outcome of each attempt.
     * Disabled by default.
     *
     * @return The retry configuration
     * @since 5.3.0
     */
    @Experimental
    public ExchangeRetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }

    /**
     * @param retryConfiguration The retry configuration of the service, see
     *                           {@link #getRetryConfiguration()}
     * @since 5.3.0
     */
    @Experimental
    @Inject
    public void setRetryConfiguration(@Nullable ServiceExchangeRetryConfiguration retryConfiguration) {
        // a service with no retry settings of its own can be offered the bean of another service
        if (retryConfiguration != null && (retryConfiguration.serviceId == null || retryConfiguration.serviceId.equals(serviceId))) {
            this.retryConfiguration = retryConfiguration;
        }
    }

    /**
     * The retry of the exchanges of this service, if it is enabled. <b>Internal API.</b>
     *
     * @return The retry, or {@code null} if it is disabled
     * @throws io.micronaut.context.exceptions.ConfigurationException if its configuration is
     * invalid, e.g. its policy does not exist
     * @since 5.3.0
     */
    @Internal
    public @Nullable ExchangeRetry exchangeRetry() {
        return retryConfiguration.exchangeRetry(serviceId);
    }

    /**
     * What the load balancer of a service does when its health check finds every instance
     * unhealthy, see {@link #getHealthCheckAllUnhealthy()}.
     *
     * @since 5.3.0
     */
    public enum HealthCheckAllUnhealthy {
        /**
         * Use every instance anyway, as the panic mode of Envoy does: requests are still tried
         * rather than failing at once. The default.
         */
        USE_ALL,
        /**
         * Use no instance: requests fail at once with no available instance, as they did when
         * the health check removed every configured URL.
         */
        NONE
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    public static class ServiceConnectionPoolConfiguration extends ConnectionPoolConfiguration {
        /**
         * Default constructor.
         */
        public ServiceConnectionPoolConfiguration() {
        }

        /**
         * Creates a service configuration initialized from the default HTTP client configuration.
         *
         * @param defaultConfiguration The default HTTP client configuration
         */
        @Inject
        public ServiceConnectionPoolConfiguration(HttpClientConfiguration defaultConfiguration) {
            super(defaultConfiguration.getConnectionPoolConfiguration());
        }
    }

    /**
     * The retry configuration of the exchanges of the service, under
     * {@code micronaut.http.services.<id>.retry}.
     *
     * @since 5.3.0
     */
    @Experimental
    @ConfigurationProperties(ExchangeRetryConfiguration.PREFIX)
    public static class ServiceExchangeRetryConfiguration extends ExchangeRetryConfiguration {
        @Nullable
        private final String serviceId;
        @Nullable
        private final RetryRegistry retryRegistry;
        private final AtomicReference<@Nullable ExchangeRetry> exchangeRetry = new AtomicReference<>();

        /**
         * @param serviceId     The id of the service this configuration belongs to
         * @param retryRegistry The registry of the named retry policies
         * @param budget        The retry budget
         */
        @Inject
        public ServiceExchangeRetryConfiguration(@Parameter @Nullable String serviceId, @Nullable RetryRegistry retryRegistry, @Nullable ServiceBudgetConfiguration budget) {
            super(budget != null ? budget : new ServiceBudgetConfiguration());
            this.serviceId = serviceId;
            this.retryRegistry = retryRegistry;
        }

        @Nullable
        final ExchangeRetry exchangeRetry(String serviceId) {
            if (!isEnabled()) {
                return null;
            }
            ExchangeRetry retry = exchangeRetry.get();
            if (retry == null) {
                // one instance per service, so all calls share its retry budget
                exchangeRetry.compareAndSet(null, PolicyExchangeRetry.create(serviceId, this, retryRegistry));
                retry = exchangeRetry.get();
            }
            return retry;
        }

        /**
         * The retry budget of the service, under
         * {@code micronaut.http.services.<id>.retry.budget}.
         */
        @ConfigurationProperties("budget")
        public static class ServiceBudgetConfiguration extends BudgetConfiguration {
        }
    }

    /**
     * The outlier detection configuration of the service.
     *
     * @since 5.3.0
     */
    @ConfigurationProperties(OutlierDetectionConfiguration.PREFIX)
    public static class ServiceOutlierDetectionConfiguration extends OutlierDetectionConfiguration {
    }

    /**
     * The default WebSocket compression configuration.
     */
    @ConfigurationProperties(WebSocketCompressionConfiguration.PREFIX)
    public static class ServiceWebSocketCompressionConfiguration extends WebSocketCompressionConfiguration {
    }

    /**
     * The service HTTP/2 configuration.
     */
    @ConfigurationProperties(Http2ClientConfiguration.PREFIX)
    public static class ServiceHttp2ClientConfiguration extends Http2ClientConfiguration {
        /**
         * Default constructor.
         */
        public ServiceHttp2ClientConfiguration() {
        }

        /**
         * Creates a service configuration initialized from the default HTTP client configuration.
         *
         * @param defaultConfiguration The default HTTP client configuration
         */
        @Inject
        public ServiceHttp2ClientConfiguration(HttpClientConfiguration defaultConfiguration) {
            super(defaultConfiguration.getHttp2Configuration());
        }
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties("ssl")
    public static class ServiceSslClientConfiguration extends AbstractClientSslConfiguration {

        public ServiceSslClientConfiguration() {
            // Enable by default, like ClientSslConfiguration; otherwise a per-service ssl block leaves SSL disabled.
            setEnabled(true);
        }

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

    /**
     * The TLS configuration of the connection to the HTTP proxy of this service, bound from
     * {@code micronaut.http.services.<id>.proxy-ssl}. Configuring it enables TLS to the proxy,
     * unless {@code enabled} is set to {@code false}.
     *
     * @since 5.3.0
     */
    @ConfigurationProperties("proxy-ssl")
    public static class ServiceProxySslClientConfiguration extends AbstractClientSslConfiguration {

        /**
         * Creates the configuration, enabled by default.
         */
        public ServiceProxySslClientConfiguration() {
            setEnabled(true);
        }

        /**
         * Sets the key configuration.
         *
         * @param keyConfiguration The key configuration.
         */
        void setKey(@Nullable ProxyKeyConfiguration keyConfiguration) {
            if (keyConfiguration != null) {
                super.setKey(keyConfiguration);
            }
        }

        /**
         * Sets the key store.
         *
         * @param keyStoreConfiguration The key store configuration
         */
        void setKeyStore(@Nullable ProxyKeyStoreConfiguration keyStoreConfiguration) {
            if (keyStoreConfiguration != null) {
                super.setKeyStore(keyStoreConfiguration);
            }
        }

        /**
         * Sets trust store configuration.
         *
         * @param trustStore The trust store configuration
         */
        void setTrustStore(@Nullable ProxyTrustStoreConfiguration trustStore) {
            if (trustStore != null) {
                super.setTrustStore(trustStore);
            }
        }

        /**
         * The key configuration of the connection to the proxy.
         */
        @ConfigurationProperties(SslConfiguration.KeyConfiguration.PREFIX)
        public static class ProxyKeyConfiguration extends SslConfiguration.KeyConfiguration {
        }

        /**
         * The key store configuration of the connection to the proxy.
         */
        @ConfigurationProperties(SslConfiguration.KeyStoreConfiguration.PREFIX)
        public static class ProxyKeyStoreConfiguration extends SslConfiguration.KeyStoreConfiguration {
        }

        /**
         * The trust store configuration of the connection to the proxy.
         */
        @ConfigurationProperties(SslConfiguration.TrustStoreConfiguration.PREFIX)
        public static class ProxyTrustStoreConfiguration extends SslConfiguration.TrustStoreConfiguration {
        }
    }
}
