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

//import javax.validation.constraints.NotNull;
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
}
