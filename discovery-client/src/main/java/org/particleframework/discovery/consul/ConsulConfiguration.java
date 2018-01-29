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
import org.particleframework.discovery.registration.RegistrationConfiguration;

//import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for consul
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties("consul")
public class ConsulConfiguration {

    private String host = "localhost";

    private int port = 8500;

    private String aslToken;

    private ConsulRegistrationConfiguration registration = new ConsulRegistrationConfiguration();

    /**
     * @return The token to include in all requests as the {@code X-Consul-Token} header
     */
    public Optional<String> getAslToken() {
        return Optional.ofNullable(aslToken);
    }

    public void setAslToken(String aslToken) {
        this.aslToken = aslToken;
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

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return The registration configuration
     */
    public ConsulRegistrationConfiguration getRegistration() {
        return registration;
    }

    public void setRegistration(ConsulRegistrationConfiguration registration) {
        this.registration = registration;
    }

    @ConfigurationProperties("registration")
    public static class ConsulRegistrationConfiguration extends RegistrationConfiguration{
        private List<String> tags = Collections.emptyList();

        /**
         * @return That tags to use for registering the service
         */
        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
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
