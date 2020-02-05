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
package io.micronaut.discovery.eureka;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.eureka.client.v2.DataCenterInfo;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.discovery.eureka.client.v2.InstanceInfo;
import io.micronaut.discovery.eureka.client.v2.LeaseInfo;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Configuration options for the Eureka client.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(EurekaConfiguration.PREFIX)
public class EurekaConfiguration extends DiscoveryClientConfiguration {

    /**
     * The prefix to use for all Eureka client settings.
     */
    public static final String PREFIX = "eureka.client";

    /**
     * The configuration name for Eureka context path.
     */
    public static final String CONTEXT_PATH = PREFIX + ".context-path";

    /**
     * The configuration name for Eureka context path.
     */
    public static final String CONTEXT_PATH_PLACEHOLDER = "${" + CONTEXT_PATH + ":/eureka}";

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
     * @param applicationConfiguration          The application configuration
     * @param eurekaRegistrationConfiguration   The optional Eureka registration configuration
     */
    public EurekaConfiguration(
            EurekaConnectionPoolConfiguration eurekaConnectionPoolConfiguration,
            ApplicationConfiguration applicationConfiguration,
            @Nullable EurekaRegistrationConfiguration eurekaRegistrationConfiguration) {
        super(applicationConfiguration);
        this.registration = eurekaRegistrationConfiguration;
        this.eurekaConnectionPoolConfiguration = eurekaConnectionPoolConfiguration;
        setPort(EUREKA_DEFAULT_PORT);
    }

    /**
     * @return The default discovery configuration
     */
    @Override
    @NonNull
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
         * Configuration property name for Eureka instance IP address.
         */
        public static final String IP_ADDRESS = PREFIX + ".ip-addr";

        /**
         * Configuration property name for preferring Eureka instance IP address registration.
         */
        public static final String PREFER_IP_ADDRESS = PREFIX + ".prefer-ip-address";

        /**
         * Configuration property name for Eureka instance app name (value: <b>{@value}</b>).
         */
        public static final String APPNAME = PREFIX + ".appname";

        /**
         * Configuration property name for Eureka instance id (value: <b>{@value}</b>).
         */
        public static final String INSTANCE_ID = PREFIX + ".instance-id";

        /**
         * Configuration property name for Eureka instance hostname (value: <b>{@value}</b>).
         */
        public static final String HOSTNAME = PREFIX + ".hostname";

        /**
         * Configuration property name for Eureka instance port (value: <b>{@value}</b>).
         */
        public static final String PORT = PREFIX + ".port";

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
         * @deprecated Use {@link #EurekaRegistrationConfiguration(EmbeddedServer, ApplicationConfiguration, DataCenterInfo, String, String, Integer, String, String, Boolean)} instead
         */
        @Deprecated
        public EurekaRegistrationConfiguration(
                EmbeddedServer embeddedServer,
                ApplicationConfiguration applicationConfiguration,
                @Property(name = EurekaRegistrationConfiguration.IP_ADDRESS) @Nullable String ipAddress,
                @Nullable DataCenterInfo dataCenterInfo) {
            String instanceId = applicationConfiguration.getInstance().getId().orElse(null);
            String applicationName = applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
            boolean explicitInstanceId = instanceId != null;
            setPreferIpAddress(embeddedServer.getApplicationContext().get(PREFER_IP_ADDRESS, Boolean.class).orElse(false));
            String serverHost = embeddedServer.getHost();
            int serverPort = embeddedServer.getPort();
            final boolean preferIpAddress = isPreferIpAddress();
            if (ipAddress != null) {
                this.instanceInfo = new InstanceInfo(
                        preferIpAddress ? ipAddress : serverHost,
                        serverPort,
                        ipAddress,
                        applicationName,
                        explicitInstanceId ? instanceId : applicationName
                );

            } else {
                if (preferIpAddress) {
                    ipAddress = lookupIp(serverHost);
                    this.instanceInfo = new InstanceInfo(
                            ipAddress,
                            serverPort,
                            ipAddress,
                            applicationName,
                            explicitInstanceId ? instanceId : applicationName
                    );
                } else {
                    this.instanceInfo = new InstanceInfo(
                            serverHost,
                            serverPort,
                            applicationName,
                            explicitInstanceId ? instanceId : applicationName
                    );
                }
            }
            if (dataCenterInfo != null) {
                this.instanceInfo.setDataCenterInfo(dataCenterInfo);
            }
            this.explicitInstanceId = false;
        }

        /**
         * @param embeddedServer           The embedded server
         * @param applicationConfiguration The application configuration
         * @param dataCenterInfo           The data center info
         * @param appName                  The application name
         * @param hostname                 The hostname
         * @param port                     The port
         * @param ipAddr                The IP address
         * @param instanceId               The instance ID
         * @param preferIpAddress          Whether the IP address is prefered
         */
        @Inject
        public EurekaRegistrationConfiguration(
                EmbeddedServer embeddedServer,
                ApplicationConfiguration applicationConfiguration,
                @Nullable DataCenterInfo dataCenterInfo,
                @Property(name = APPNAME) @Nullable String appName,
                @Property(name = HOSTNAME) @Nullable String hostname,
                @Property(name = PORT) @Nullable Integer port,
                @Property(name = IP_ADDRESS) @Nullable String ipAddr,
                @Property(name = INSTANCE_ID) @Nullable String instanceId,
                @Property(name = PREFER_IP_ADDRESS) @Nullable Boolean preferIpAddress) {
            if (preferIpAddress == null) {
                preferIpAddress = false;
            }
            if (StringUtils.isEmpty(appName)) {
                appName = applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
            }
            if (StringUtils.isEmpty(hostname)) {
                hostname = embeddedServer.getHost();
            }
            if (StringUtils.isEmpty(ipAddr)) {
                ipAddr = lookupIp(hostname);
            }
            if (port == null) {
                port = embeddedServer.getPort();
            }

            this.explicitInstanceId = StringUtils.isNotEmpty(instanceId) || preferIpAddress;
            if (!explicitInstanceId || StringUtils.isEmpty(instanceId)) {
                String appHostname = preferIpAddress ? ipAddr : hostname;
                instanceId = appHostname + ":" + appName + ":" + port;
            }
            this.instanceInfo = createInstanceInfo(appName, instanceId, hostname, ipAddr, port, preferIpAddress);
            if (dataCenterInfo != null) {
                this.instanceInfo.setDataCenterInfo(dataCenterInfo);
            }
        }

        private InstanceInfo createInstanceInfo(String appName, String instanceId, String hostname, String ipAddr,
                                                int port,
                                                boolean preferIpAddress) {
            String appHostname = preferIpAddress ? ipAddr : hostname;
            return new InstanceInfo(appHostname, port, ipAddr, appName, instanceId);
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

        private static String lookupIp(String host) {
            try {
                return InetAddress.getByName(host).getHostAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unable to lookup host IP address: " + host, e);
            }
        }

    }
}
