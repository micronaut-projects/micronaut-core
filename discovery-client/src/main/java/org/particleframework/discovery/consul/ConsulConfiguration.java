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
package org.particleframework.discovery.consul;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.util.Toggleable;
import org.particleframework.discovery.DiscoveryConfiguration;
import org.particleframework.discovery.consul.client.v1.ConsulClient;
import org.particleframework.discovery.registration.RegistrationConfiguration;
import org.particleframework.http.HttpMethod;

//import javax.validation.constraints.NotNull;
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
@ConfigurationProperties(ConsulClient.SERVICE_ID)
public class ConsulConfiguration {

    private String host = "localhost";

    private int port = 8500;

    private String aslToken;

    private ConsulRegistrationConfiguration registration = new ConsulRegistrationConfiguration();

    private ConsulDiscoveryConfiguration discovery = new ConsulDiscoveryConfiguration();
    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     */
    public Optional<String> getAslToken() {
        return Optional.ofNullable(aslToken);
    }

    /**
     * @return The agent host name. Defaults to 'localhost'.
     **/
    public String getHost() {
        return host;
    }

    /**
     * @return The agent port. Defaults to 'localhost'.
     **/
    public int getPort() {
        return port;
    }

    /**
     * @return The registration configuration
     */
    public ConsulRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @return The discovery configuration
     */
    public ConsulDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    public void setDiscovery(ConsulDiscoveryConfiguration discovery) {
        this.discovery = discovery;
    }

    public void setAslToken(String aslToken) {
        this.aslToken = aslToken;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setRegistration(ConsulRegistrationConfiguration registration) {
        this.registration = registration;
    }

    @ConfigurationProperties("discovery")
    public static class ConsulDiscoveryConfiguration extends DiscoveryConfiguration {
        private Map<String, List<String>> tags = Collections.emptyMap();
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
        public Map<String, List<String>> getTags() {
            return tags;
        }

        /**
         * A map of service ID to data centers to query
         * @return The map to query
         */
        public Map<String, String> getDatacenters() {
            return datacenters;
        }

        public void setPassing(boolean passing) {
            this.passing = passing;
        }

        public void setTags(Map<String, List<String>> tags) {
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
                "host='" + host + '\'' +
                ", port=" + port +
                ", aslToken='" + aslToken + '\'' +
                ", registration=" + registration +
                '}';
    }
}
