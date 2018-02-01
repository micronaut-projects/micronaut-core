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
package org.particleframework.discovery.eureka;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.util.StringUtils;
import org.particleframework.discovery.DiscoveryConfiguration;
import org.particleframework.discovery.registration.RegistrationConfiguration;
import org.particleframework.http.client.HttpClientConfiguration;
import org.particleframework.runtime.ApplicationConfiguration;

import javax.annotation.Nonnull;

/**
 * Configuration options for the Eureka client
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(EurekaConfiguration.PREFIX)
public class EurekaConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = "eureka.client";
    public static final String HOST = PREFIX + ".host";
    public static final String PORT = PREFIX + ".port";

    private String host = LOCALHOST;
    private int port = 8761;
    private boolean secure;
    private EurekaDiscoveryConfiguration discovery = new EurekaDiscoveryConfiguration();
    private EurekaRegistrationConfiguration registration = new EurekaRegistrationConfiguration();

    public EurekaConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * @return The Eureka instance host name. Defaults to 'localhost'.
     **/
    @Nonnull public String getHost() {
        return host;
    }

    /**
     * @return The default Eureka port
     */
    public int getPort() {
        return port;
    }

    /**
     * @return The default discovery configuration
     */
    @Nonnull public EurekaDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    /**
     * @return The default registration configuration
     */
    @Nonnull public EurekaRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @return Is eureka exposed over HTTPS (defaults to false)
     */
    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public void setDiscovery(EurekaDiscoveryConfiguration discovery) {
        if(discovery != null) {
            this.discovery = discovery;
        }
    }

    public void setRegistration(EurekaRegistrationConfiguration registration) {
        if(registration != null) {
            this.registration = registration;
        }
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setHost(String host) {
        if(StringUtils.isNotEmpty(host)) {
            this.host = host;
        }
    }

    public boolean shouldLogAmazonMetadataErrors() {
        return true;
    }


    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    public static class EurekaDiscoveryConfiguration extends DiscoveryConfiguration {
    }

    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    public static class EurekaRegistrationConfiguration extends RegistrationConfiguration{
    }

}
