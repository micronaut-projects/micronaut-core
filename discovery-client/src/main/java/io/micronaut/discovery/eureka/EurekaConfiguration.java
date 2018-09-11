/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.discovery.eureka;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.eureka.client.v2.DataCenterInfo;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.discovery.eureka.client.v2.InstanceInfo;
import io.micronaut.discovery.eureka.client.v2.LeaseInfo;
import io.micronaut.discovery.eureka.condition.RequiresEureka;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Configuration options for the Eureka client.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(EurekaConfiguration.PREFIX)
@RequiresEureka
public class EurekaConfiguration extends DiscoveryClientConfiguration {

    /**
     * The prefix to use for all Eureka client settings.
     */
    public static final String PREFIX = "eureka.client";

    /**
     * The configuration name for Eureka host.
     */
    public static final String HOST = PREFIX + ".host";

    /**
     * The configuration name for Eureka port.
     */
    public static final String PORT = PREFIX + ".port";

    private static final int EUREKA_DEFAULT_PORT = 8761;

    private final ConnectionPoolConfiguration eurekaConnectionPoolConfiguration;
    private EurekaDiscoveryConfiguration discovery = new EurekaDiscoveryConfiguration();
    private EurekaRegistrationConfiguration registration;

    /**
     * @param eurekaConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration        The application configuration
     * @param eurekaRegistrationConfiguration The optional Eureka registration configuration
     */
    public EurekaConfiguration(
        EurekaConnectionPoolConfiguration eurekaConnectionPoolConfiguration,
        ApplicationConfiguration applicationConfiguration,
        Optional<EurekaRegistrationConfiguration> eurekaRegistrationConfiguration) {
        super(applicationConfiguration);
        this.registration = eurekaRegistrationConfiguration.orElse(null);
        this.eurekaConnectionPoolConfiguration = eurekaConnectionPoolConfiguration;
        setPort(EUREKA_DEFAULT_PORT);
    }

    /**
     * @return The default discovery configuration
     */
    @Override
    @Nonnull
    public EurekaDiscoveryConfiguration getDiscovery() {
        return discovery;
    }

    /**
     * @param discovery The discovery configuration
     */
    public void setDiscovery(EurekaDiscoveryConfiguration discovery) {
        if (discovery != null) {
            this.discovery = discovery;
        }
    }

    /**
     * @return The default registration configuration
     */
    @Override
    @Nullable
    public EurekaRegistrationConfiguration getRegistration() {
        return registration;
    }

    /**
     * @return Whether should log Amazon Metadata errors
     */
    public boolean shouldLogAmazonMetadataErrors() {
        return true;
    }

    /**
     * @return The Service ID
     */
    @Override
    protected String getServiceID() {
        return EurekaClient.SERVICE_ID;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return this.eurekaConnectionPoolConfiguration;
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    public static class EurekaConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * Configuration properties for Eureka client discovery.
     */
    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    public static class EurekaDiscoveryConfiguration extends DiscoveryConfiguration {

        private boolean useSecurePort;

        /**
         * @return Whether the secure port is used for communication.
         */
        public boolean isUseSecurePort() {
            return useSecurePort;
        }

        /**
         * Sets whether the secure port is used for communication.
         *
         * @param useSecurePort True if the secure port should be used
         */
        public void setUseSecurePort(boolean useSecurePort) {
            this.useSecurePort = useSecurePort;
        }
    }

    /**
     * Configuration properties for Eureka client registration.
     */
    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    @Requires(property = ApplicationConfiguration.APPLICATION_NAME)
    public static class EurekaRegistrationConfiguration extends RegistrationConfiguration {

        /**
         * Prefix for Eureka registration client.
         */
        public static final String PREFIX = EurekaConfiguration.PREFIX + "." + RegistrationConfiguration.PREFIX;

        /**
         * Configuration name property for Eureka IP address.
         */
        public static final String IP_ADDRESS =
            EurekaConfiguration.PREFIX + '.' +
                RegistrationConfiguration.PREFIX + '.' +
                "ip-addr";

        @ConfigurationBuilder
        InstanceInfo instanceInfo;

        @ConfigurationBuilder(configurationPrefix = "lease-info")
        LeaseInfo.Builder leaseInfo = LeaseInfo.Builder.newBuilder();

        private final boolean explicitInstanceId;

        /**
         * @param embeddedServer           The embedded server
         * @param applicationConfiguration The application configuration
         * @param ipAddress                The IP address
         * @param dataCenterInfo           The data center info
         */
        public EurekaRegistrationConfiguration(
            EmbeddedServer embeddedServer,
            ApplicationConfiguration applicationConfiguration,
            @Value("${" + EurekaRegistrationConfiguration.IP_ADDRESS + "}") @Nullable String ipAddress,
            @Nullable DataCenterInfo dataCenterInfo) {
            String instanceId = applicationConfiguration.getInstance().getId().orElse(null);
            String applicationName = applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
            this.explicitInstanceId = instanceId != null;
            if (ipAddress != null) {
                this.instanceInfo = new InstanceInfo(
                    embeddedServer.getHost(),
                    embeddedServer.getPort(),
                    ipAddress,
                    applicationName,
                    explicitInstanceId ? instanceId : applicationName
                );

            } else {
                this.instanceInfo = new InstanceInfo(
                    embeddedServer.getHost(),
                    embeddedServer.getPort(),
                    applicationName,
                    explicitInstanceId ? instanceId : applicationName
                );
            }

            if (dataCenterInfo != null) {
                this.instanceInfo.setDataCenterInfo(dataCenterInfo);
            }
        }

        /**
         * @return Is an instance ID explicitly specified
         */
        public boolean isExplicitInstanceId() {
            return explicitInstanceId;
        }

        /**
         * @return The instance info
         */
        public InstanceInfo getInstanceInfo() {
            LeaseInfo leaseInfo = this.leaseInfo.build();
            instanceInfo.setLeaseInfo(leaseInfo);
            return instanceInfo;
        }
    }
}
