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
 * Configuration options for the Eureka client
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(EurekaConfiguration.PREFIX)
@RequiresEureka
public class EurekaConfiguration extends DiscoveryClientConfiguration {

    /**
     * The prefix to use for all Eureka client settings
     */
    public static final String PREFIX = "eureka.client";

    /**
     * The configuration name for Eureka host
     */
    public static final String HOST = PREFIX + ".host";

    /**
     * The configuration name for Eureka port
     */
    public static final String PORT = PREFIX + ".port";

    private EurekaDiscoveryConfiguration discovery = new EurekaDiscoveryConfiguration();
    private EurekaRegistrationConfiguration registration;

    public EurekaConfiguration(
        ApplicationConfiguration applicationConfiguration,
        Optional<EurekaRegistrationConfiguration> eurekaRegistrationConfiguration) {
        super(applicationConfiguration);
        this.registration = eurekaRegistrationConfiguration.orElse(null);
        setPort(8761);
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

    /**
     * Configuration properties for Eureka client discovery
     */
    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    public static class EurekaDiscoveryConfiguration extends DiscoveryConfiguration {
    }

    /**
     * Configuration properties for Eureka client registration
     */
    @ConfigurationProperties(RegistrationConfiguration.PREFIX)
    @Requires(property = ApplicationConfiguration.APPLICATION_NAME)
    public static class EurekaRegistrationConfiguration extends RegistrationConfiguration {

        /**
         * Prefix for Eureka registration client
         */
        public static final String PREFIX = EurekaConfiguration.PREFIX + "." + RegistrationConfiguration.PREFIX;

        /**
         * Configuration name property for Eureka IP address
         */
        public static final String IP_ADDRESS =
            EurekaConfiguration.PREFIX + '.' +
                RegistrationConfiguration.PREFIX + '.' +
                "ipAddr";

        @ConfigurationBuilder
        InstanceInfo instanceInfo;

        @ConfigurationBuilder(configurationPrefix = "leaseInfo")
        LeaseInfo.Builder leaseInfo = LeaseInfo.Builder.newBuilder();

        private final boolean explicitInstanceId;

        public EurekaRegistrationConfiguration(
            EmbeddedServer embeddedServer,
            @Value("${" + ApplicationConfiguration.APPLICATION_NAME + "}") String applicationName,
            @Value("${" + EurekaRegistrationConfiguration.IP_ADDRESS + "}") Optional<String> ipAddress,
            @Value("${" + ApplicationConfiguration.InstanceConfiguration.INSTANCE_ID + "}") Optional<String> instanceId,
            Optional<DataCenterInfo> dataCenterInfo) {
            this.explicitInstanceId = instanceId.isPresent();
            if (ipAddress.isPresent()) {
                this.instanceInfo = new InstanceInfo(
                    embeddedServer.getHost(),
                    embeddedServer.getPort(),
                    ipAddress.get(),
                    applicationName,
                    instanceId.orElse(applicationName));

            } else {
                this.instanceInfo = new InstanceInfo(
                    embeddedServer.getHost(),
                    embeddedServer.getPort(),
                    applicationName,
                    instanceId.orElse(applicationName));
            }

            dataCenterInfo.ifPresent(dci -> this.instanceInfo.setDataCenterInfo(dci));
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
