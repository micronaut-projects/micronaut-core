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
package io.micronaut.discovery.consul;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.HttpMethod;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

//import javax.validation.constraints.NotNull;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for consul
 *
 * @author graemerocher
 * @since 1.0
 */
@RequiresConsul
@ConfigurationProperties(ConsulConfiguration.PREFIX)
public class ConsulConfiguration extends DiscoveryClientConfiguration {

    public static final String PREFIX = "consul.client";

    private String aslToken;

    private ConsulRegistrationConfiguration registration = new ConsulRegistrationConfiguration();

    private ConsulDiscoveryConfiguration discovery = new ConsulDiscoveryConfiguration();

    private ConsulConfigDiscoveryConfiguration configuration = new ConsulConfigDiscoveryConfiguration();

    public ConsulConfiguration() {
        setPort(8500);
    }

    @Inject
    public ConsulConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        setPort(8500);
    }

    /**
     * @return The configuration discovery configuration
     */
    public ConsulConfigDiscoveryConfiguration getConfiguration() {
        return configuration;
    }

    @Inject
    public void setConfiguration(ConsulConfigDiscoveryConfiguration configuration) {
        if(configuration != null)
            this.configuration = configuration;
    }

    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     */
    public Optional<String> getAslToken() {
        return Optional.ofNullable(aslToken);
    }

    /**
     * @return The registration configuration
     */
    @Override
    public ConsulRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @return The discovery configuration
     */
    @Override
    public ConsulDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    @Inject
    public void setDiscovery(ConsulDiscoveryConfiguration discovery) {
        if(discovery != null)
            this.discovery = discovery;
    }

    public void setAslToken(String aslToken) {
        this.aslToken = aslToken;
    }

    @Override
    protected String getServiceID() {
        return ConsulClient.SERVICE_ID;
    }

    @Inject
    public void setRegistration(ConsulRegistrationConfiguration registration) {
        if(registration != null)
            this.registration = registration;
    }

    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    public static class ConsulConfigDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        private String datacenter;

        /**
         * The data center to use to read configuration
         * @return The data center name
         */
        public Optional<String> getDatacenter() {
            return Optional.ofNullable(datacenter);
        }

        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }
    }

    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    public static class ConsulDiscoveryConfiguration extends DiscoveryConfiguration {
        private Map<String, String> tags = Collections.emptyMap();
        private Map<String, String> schemes = Collections.emptyMap();
        private Map<String, String> datacenters = Collections.emptyMap();
        private boolean passing = false;

        /**
         * Whether services that are not passing health checks should be returned
         * @return True if only passing services should be returned (defaults to false)
         */
        public boolean isPassing() {
            return passing;
        }

        /**
         * A map of service ID to tags to use for querying
         * @return The tags
         */
        public Map<String, String> getTags() {
            return tags;
        }

        /**
         * A map of service ID to data centers to query
         * @return The map to query
         */
        public Map<String, String> getDatacenters() {
            return datacenters;
        }

        /**
         * A map of service ID to protocol scheme (eg. http, https etc.). Default is http
         * @return A map of schemes
         */
        public Map<String, String> getSchemes() {
            return schemes;
        }

        public void setSchemes(Map<String, String> schemes) {
            this.schemes = schemes;
        }

        public void setPassing(boolean passing) {
            this.passing = passing;
        }

        public void setTags(Map<String, String> tags) {
            if(tags != null) {
                this.tags = tags;
            }
        }

        public void setDatacenters(Map<String, String> datacenters) {
            if(datacenters != null) {
                this.datacenters = datacenters;
            }
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

    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    public static class ConsulRegistrationConfiguration extends RegistrationConfiguration{

        public static final String PREFIX = ConsulConfiguration.PREFIX + "." + RegistrationConfiguration.PREFIX;

        private List<String> tags = Collections.emptyList();
        private CheckConfiguration check = new CheckConfiguration();

        /**
         * @return That tags to use for registering the service
         */
        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public CheckConfiguration getCheck() {
            return check;
        }

        public void setCheck(CheckConfiguration check) {
            this.check = check;
        }

        /**
         * Configuration for the HTTP check. See https://www.consul.io/api/agent/check.html
         */
        @ConfigurationProperties("check")
        public static class CheckConfiguration implements Toggleable {
            private HttpMethod method = HttpMethod.GET;
            private Duration interval = Duration.ofSeconds(15);
            private Map<CharSequence, List<String>> headers = Collections.emptyMap();
            private Duration deregisterCriticalServiceAfter;
            private String notes;
            private String id;
            private Boolean tlsSkipVerify;
            private boolean enabled = true;
            private boolean http = false;

            public Duration getInterval() {
                return interval;
            }

            public void setInterval(Duration interval) {
                this.interval = interval;
            }

            /**
             * @return Whether to perform an HTTP check
             */
            public boolean isHttp() {
                return http;
            }

            public void setHttp(boolean http) {
                this.http = http;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Optional<Duration> getDeregisterCriticalServiceAfter() {
                return Optional.ofNullable(deregisterCriticalServiceAfter);
            }

            public Optional<String> getId() {
                return Optional.ofNullable(id);
            }

            public void setId(String id) {
                this.id = id;
            }

            public Optional<String> getNotes() {
                return Optional.ofNullable(notes);
            }

            public void setNotes(String notes) {
                this.notes = notes;
            }

            public void setDeregisterCriticalServiceAfter(Duration deregisterCriticalServiceAfter) {
                this.deregisterCriticalServiceAfter = deregisterCriticalServiceAfter;
            }

            public Optional<Boolean> getTlsSkipVerify() {
                return Optional.ofNullable(tlsSkipVerify);
            }

            public void setTlsSkipVerify(Boolean tlsSkipVerify) {
                this.tlsSkipVerify = tlsSkipVerify;
            }

            public HttpMethod getMethod() {
                return method;
            }

            public void setMethod(HttpMethod method) {
                this.method = method;
            }

            public Map<CharSequence, List<String>> getHeaders() {
                return headers;
            }

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

        @Override
        public String toString() {
            return "ConsulRegistrationConfiguration{" +
                    "tags=" + tags +
                    ", check=" + check +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ConsulConfiguration{" +
                "aslToken='" + aslToken + '\'' +
                ", registration=" + registration +
                ", discovery=" + discovery +
                "} " + super.toString();
    }
}
