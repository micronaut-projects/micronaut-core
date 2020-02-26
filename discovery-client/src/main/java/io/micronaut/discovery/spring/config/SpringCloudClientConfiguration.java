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
package io.micronaut.discovery.spring.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;

/**
 * A {@link HttpClientConfiguration} for Spring Cloud Config.
 *
 *  @author Thiago Locatelli
 *  @author graemerocher
 *  @since 1.0
 */
@ConfigurationProperties(SpringCloudClientConfiguration.PREFIX)
@BootstrapContextCompatible
public class SpringCloudClientConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = "spring.cloud.config";

    private static final String DEFAULT_URI = "http://localhost:8888";
    public static final String SPRING_CLOUD_CONFIG_ENDPOINT = "${" + SpringCloudClientConfiguration.PREFIX + ".uri:`" + DEFAULT_URI + "`}";

    private static final Boolean DEFAULT_FAIL_FAST = false;

    private String uri = DEFAULT_URI;
    private String label;
    private boolean failFast = DEFAULT_FAIL_FAST;

    private final SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration;
    private final SpringConfigDiscoveryConfiguration springConfigDiscoveryConfiguration = new SpringConfigDiscoveryConfiguration();

    /**
     * Default constructor.
     */
    public SpringCloudClientConfiguration() {
        this.springCloudConnectionPoolConfiguration = new SpringCloudConnectionPoolConfiguration();
    }

    /**
     * @param springCloudConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public SpringCloudClientConfiguration(SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.springCloudConnectionPoolConfiguration = springCloudConnectionPoolConfiguration;
    }

    @Override
    public @Nonnull ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return springCloudConnectionPoolConfiguration;
    }

    /**
     * @return The configuration discovery configuration
     */
    public @Nonnull SpringConfigDiscoveryConfiguration getConfiguration() {
        return springConfigDiscoveryConfiguration;
    }

    /**
     * @return The spring cloud config server uri
     */
    public @Nonnull Optional<String> getUri() {
        return uri != null ? Optional.of(uri) : Optional.empty();
    }

    /**
     * Set the Spring Cloud config server uri.  Default value ({@value #DEFAULT_URI}).
     *
     * @param uri Spring Cloud config server uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return The spring cloud config server label
     */
    public String getLabel() {
        return label;
    }

    /**
     *
     * @return Flag to indicate that failure to connect to Spring Cloud Config is fatal (default false).
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     *
     * If set to true an exception will be thrown if configuration is not found.
     * Default value ({@value #DEFAULT_FAIL_FAST}).
     *
     * @param failFast  flag to fail fast
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * Set the Spring Cloud config server label.
     *
     * @param label Spring Cloud config server label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class SpringCloudConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * Configuration class for Consul client config.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class SpringConfigDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        /**
         * The full prefix for this configuration.
         */
        public static final String PREFIX = SpringCloudClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;

    }
}
