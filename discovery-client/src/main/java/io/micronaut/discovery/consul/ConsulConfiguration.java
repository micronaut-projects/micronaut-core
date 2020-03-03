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
package io.micronaut.discovery.consul;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.http.HttpMethod;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for consul.
 *
 * @author graemerocher
 * @since 1.0
 */
@RequiresConsul
@ConfigurationProperties(ConsulConfiguration.PREFIX)
@BootstrapContextCompatible
public class ConsulConfiguration extends DiscoveryClientConfiguration {

    /**
     * The prefix to use for all Consul settings.
     */
    public static final String PREFIX = "consul.client";

    private static final int CONSULT_DEFAULT_PORT = 8500;
    private final ConsulConnectionPoolConfiguration consulConnectionPoolConfiguration;

    private String aslToken;
    private boolean healthCheck = true;
    private ConsulRegistrationConfiguration registration = new ConsulRegistrationConfiguration();
    private ConsulDiscoveryConfiguration discovery = new ConsulDiscoveryConfiguration();
    private ConsulConfigDiscoveryConfiguration configuration = new ConsulConfigDiscoveryConfiguration();

    /**
     * Default Consult configuration.
     */
    public ConsulConfiguration() {
        setPort(CONSULT_DEFAULT_PORT);
        consulConnectionPoolConfiguration = new ConsulConnectionPoolConfiguration();
    }

    /**
     * @param consulConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public ConsulConfiguration(ConsulConnectionPoolConfiguration consulConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        setPort(CONSULT_DEFAULT_PORT);
        this.consulConnectionPoolConfiguration = consulConnectionPoolConfiguration;
    }

    /**
     * @return Whether the Consul server should be considered for health checks.
     * @see io.micronaut.discovery.consul.health.ConsulHealthIndicator
     */
    public boolean isHealthCheck() {
        return healthCheck;
    }

    /**
     * Sets whether the Consul server should be considered for health checks.
     * @see io.micronaut.discovery.consul.health.ConsulHealthIndicator

     * @param healthCheck True if it should
     */
    public void setHealthCheck(boolean healthCheck) {
        this.healthCheck = healthCheck;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return consulConnectionPoolConfiguration;
    }

    /**
     * @return The configuration discovery configuration
     */
    public ConsulConfigDiscoveryConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration The {@link ConsulConfigDiscoveryConfiguration}
     */
    @Inject
    public void setConfiguration(ConsulConfigDiscoveryConfiguration configuration) {
        if (configuration != null) {
            this.configuration = configuration;
        }
    }

    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     */
    public Optional<String> getAslToken() {
        return Optional.ofNullable(aslToken);
    }

    /**
     * @param aslToken The asl token
     */
    public void setAslToken(String aslToken) {
        this.aslToken = aslToken;
    }

    /**
     * @return The registration configuration
     */
    @Override
    public ConsulRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @param registration The {@link ConsulRegistrationConfiguration}
     */
    @Inject
    public void setRegistration(ConsulRegistrationConfiguration registration) {
        if (registration != null) {
            this.registration = registration;
        }
    }

    /**
     * @return The discovery configuration
     */
    @Override
    public ConsulDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    /**
     * @param discovery The {@link ConsulDiscoveryConfiguration}
     */
    @Inject
    public void setDiscovery(ConsulDiscoveryConfiguration discovery) {
        if (discovery != null) {
            this.discovery = discovery;
        }
    }

    /**
     * @return The serviceID
     */
    @Override
    protected String getServiceID() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public String toString() {
        return "ConsulConfiguration{" +
            "aslToken='" + aslToken + '\'' +
            ", registration=" + registration +
            ", discovery=" + discovery +
            "} " + super.toString();
    }

    /**
     * Configuration class for Consul client config.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulConfigDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        /**
         * The full prefix for this configuration.
         */
        public static final String PREFIX = ConsulConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;

        private String datacenter;

        /**
         * The data center to use to read configuration.
         *
         * @return The data center name
         */
        public Optional<String> getDatacenter() {
            return Optional.ofNullable(datacenter);
        }

        /**
         * @param datacenter The datacenter
         */
        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }
    }

    /**
     * Configuration class for Consul client discovery.
     */
    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulDiscoveryConfiguration extends DiscoveryConfiguration {

        private Map<String, String> tags = Collections.emptyMap();
        private Map<String, String> schemes = Collections.emptyMap();
        private Map<String, String> datacenters = Collections.emptyMap();
        private boolean passing = false;

        /**
         * Whether services that are not passing health checks should be returned.
         *
         * @return True if only passing services should be returned (defaults to false)
         */
        public boolean isPassing() {
            return passing;
        }

        /**
         * @param passing Whether services that are not passing health checks should be returned
         */
        public void setPassing(boolean passing) {
            this.passing = passing;
        }

        /**
         * A map of service ID to tags to use for querying.
         *
         * @return The tags
         */
        public Map<String, String> getTags() {
            return tags;
        }

        /**
         * @param tags The tags to use for querying
         */
        public void setTags(Map<String, String> tags) {
            if (tags != null) {
                this.tags = tags;
            }
        }

        /**
         * A map of service ID to data centers to query.
         *
         * @return The map to query
         */
        public Map<String, String> getDatacenters() {
            return datacenters;
        }

        /**
         * @param datacenters The data centers to query
         */
        public void setDatacenters(Map<String, String> datacenters) {
            if (datacenters != null) {
                this.datacenters = datacenters;
            }
        }

        /**
         * A map of service ID to protocol scheme (eg. http, https etc.). Default is http.
         *
         * @return A map of schemes
         */
        public Map<String, String> getSchemes() {
            return schemes;
        }

        /**
         * @param schemes The service ID to protocol scheme
         */
        public void setSchemes(Map<String, String> schemes) {
            this.schemes = schemes;
        }

        @Override
        public String toString() {
            return "ConsulDiscoveryConfiguration{" +
                "tags=" + tags +
                ", datacenters=" + datacenters +
                ", passing=" + passing +
                '}';
        }
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * Configuration class for Consul client registration.
     */
    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class ConsulRegistrationConfiguration extends RegistrationConfiguration {

        /**
         * The prefix to use for all Consul client registration settings.
         */
        public static final String PREFIX = ConsulConfiguration.PREFIX + "." + RegistrationConfiguration.PREFIX;

        private List<String> tags = Collections.emptyList();
        private CheckConfiguration check = new CheckConfiguration();

        /**
         * @return That tags to use for registering the service
         */
        public List<String> getTags() {
            return tags;
        }

        /**
         * @param tags The tags for registering the service
         */
        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        /**
         * @return The Consul client settings for HTTP check
         */
        public CheckConfiguration getCheck() {
            return check;
        }

        /**
         * @param check The Consul client settings for HTTP check
         */
        public void setCheck(CheckConfiguration check) {
            this.check = check;
        }

        @Override
        public String toString() {
            return "ConsulRegistrationConfiguration{" +
                "tags=" + tags +
                ", check=" + check +
                '}';
        }

        /**
         * Configuration for the HTTP check. See https://www.consul.io/api/agent/check.html.
         */
        @ConfigurationProperties("check")
        @BootstrapContextCompatible
        public static class CheckConfiguration implements Toggleable {

            /**
             * The default enable value.
             */
            @SuppressWarnings("WeakerAccess")
            public static final boolean DEFAULT_ENABLED = true;

            /**
             * The default http value.
             */
            @SuppressWarnings("WeakerAccess")
            public static final boolean DEFAULT_HTTP = false;

            /**
             * The default interval seconds.
             */
            @SuppressWarnings("WeakerAccess")
            public static final int DEFAULT_INTERVAL_SECONDS = 15;

            private HttpMethod method = HttpMethod.GET;
            private Duration interval = Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS);
            private Map<CharSequence, List<String>> headers = Collections.emptyMap();
            private Duration deregisterCriticalServiceAfter;
            private String notes;
            private String id;
            private Boolean tlsSkipVerify;
            private boolean enabled = DEFAULT_ENABLED;
            private boolean http = DEFAULT_HTTP;

            /**
             * @return The interval for the checks
             */
            public Duration getInterval() {
                return interval;
            }

            /**
             * Default value ({@value #DEFAULT_INTERVAL_SECONDS}).
             * @param interval The interval for the checks
             */
            public void setInterval(Duration interval) {
                this.interval = interval;
            }

            /**
             * @return Whether to perform an HTTP check
             */
            public boolean isHttp() {
                return http;
            }

            /**
             * Default value ({@value #DEFAULT_HTTP}).
             * @param http Whether to perform an HTTP check
             */
            public void setHttp(boolean http) {
                this.http = http;
            }

            /**
             * @return Whether the check module is enabled
             */
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * Default value ({@value #DEFAULT_ENABLED}).
             * @param enabled Whether the check module is enabled
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * @return Specifies that checks associated with a service should deregister after this time
             */
            public Optional<Duration> getDeregisterCriticalServiceAfter() {
                return Optional.ofNullable(deregisterCriticalServiceAfter);
            }

            /**
             * @param deregisterCriticalServiceAfter Specifies that checks associated with a service should deregister after this time
             */
            public void setDeregisterCriticalServiceAfter(Duration deregisterCriticalServiceAfter) {
                this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
            }

            /**
             * @return Specifies a unique ID for this check on the node
             */
            public Optional<String> getId() {
                return Optional.ofNullable(id);
            }

            /**
             * @param id The unique ID for this check on the node
             */
            public void setId(String id) {
                this.id = id;
            }

            /**
             * @return Arbitrary information for humans. Not used by Consult
             */
            public Optional<String> getNotes() {
                return Optional.ofNullable(notes);
            }

            /**
             * @param notes Arbitrary information for humans
             */
            public void setNotes(String notes) {
                this.notes = notes;
            }

            /**
             * @return Specifies if the certificate for an HTTPS check should not be verified
             */
            public Optional<Boolean> getTlsSkipVerify() {
                return Optional.ofNullable(tlsSkipVerify);
            }

            /**
             * @param tlsSkipVerify Specifies if the certificate for an HTTPS check should not be verified.
             */
            public void setTlsSkipVerify(Boolean tlsSkipVerify) {
                this.tlsSkipVerify = tlsSkipVerify;
            }

            /**
             * @return Specifies a different HTTP method to be used for an HTTP check.
             */
            public HttpMethod getMethod() {
                return method;
            }

            /**
             * @param method The HTTP method to be used for an HTTP check.
             */
            public void setMethod(HttpMethod method) {
                this.method = method;
            }

            /**
             * @return Specifies a set of headers that should be set for HTTP checks
             */
            public Map<CharSequence, List<String>> getHeaders() {
                return headers;
            }

            /**
             * @param headers Headers for the HTTP checks
             */
            public void setHeaders(Map<CharSequence, List<String>> headers) {
                this.headers = headers;
            }

            @Override
            public String toString() {
                return "CheckConfiguration{" +
                    "method=" + method +
                    ", headers=" + headers +
                    ", deregisterCriticalServiceAfter=" + deregisterCriticalServiceAfter +
                    ", notes='" + notes + '\'' +
                    ", id='" + id + '\'' +
                    ", tlsSkipVerify=" + tlsSkipVerify +
                    ", enabled=" + enabled +
                    '}';
            }
        }
    }

}
