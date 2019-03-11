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
package io.micronaut.discovery.spring;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.spring.condition.RequiresSpringCloudConfig;
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
@RequiresSpringCloudConfig
@ConfigurationProperties(SpringCloudConfigConfiguration.PREFIX)
@BootstrapContextCompatible
public class SpringCloudConfigConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = SpringCloudConstants.PREFIX + ".config";
    public static final String SPRING_CLOUD_CONFIG_ENDPOINT = "${" + SpringCloudConfigConfiguration.PREFIX + ".uri}";

    private String uri = "http://locahost:8888";

    private final SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration;
    private final SpringConfigDiscoveryConfiguration springConfigDiscoveryConfiguration = new SpringConfigDiscoveryConfiguration();

    /**
     * Default constructor.
     */
    public SpringCloudConfigConfiguration() {
        this.springCloudConnectionPoolConfiguration = new SpringCloudConnectionPoolConfiguration();
    }

    /**
     * @param springCloudConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public SpringCloudConfigConfiguration(SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
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
     * Set the Spring Cloud config server uri.
     *
     * @param uri Spring Cloud config server uri
     */
    public void setUri(String uri) {
        this.uri = uri;
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
        public static final String PREFIX = SpringCloudConfigConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;

    }
}
