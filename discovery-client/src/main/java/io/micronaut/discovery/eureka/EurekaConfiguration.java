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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.io.socket.SocketUtils;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
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
     * @param applicationConfiguration        The application configuration
     * @param eurekaRegistrationConfiguration The optional Eureka registration configuration
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
    @RequiresEureka
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    public static class EurekaConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * Configuration properties for Eureka client discovery.
     */
    @ConfigurationProperties(DiscoveryConfiguration.PREFIX)
    @RequiresEureka
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
    @RequiresEureka
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

        /**
         * @param embeddedServer           The embedded server
         * @param applicationConfiguration The application configuration
         * @param dataCenterInfo           The data center info
         */
        public EurekaRegistrationConfiguration(
                EmbeddedServer embeddedServer,
                ApplicationConfiguration applicationConfiguration,
                @Nullable DataCenterInfo dataCenterInfo) {
            this.instanceInfo = createInstanceInfo(applicationConfiguration, embeddedServer);
            if (dataCenterInfo != null) {
                this.instanceInfo.setDataCenterInfo(dataCenterInfo);
            }
        }

        private InstanceInfo createInstanceInfo(ApplicationConfiguration appConfig, EmbeddedServer server) {
            ApplicationContext appCtx = server.getApplicationContext();
            boolean preferIpAddress = appCtx.get(PREFER_IP_ADDRESS, Boolean.class).orElse(false);

            String appName = selectApplicationName(appCtx, appConfig);
            String hostname = selectEurekaHostname(appCtx, server);
            String ipAddr = selectEurekaIpAddress(appCtx, server);
            int port = selectEurekaPort(appCtx, server);

            String instanceIdHostname = preferIpAddress ? ipAddr : hostname;
            String instanceId = selectEurekaInstanceId(appCtx, appConfig, appName, instanceIdHostname, port);

            return createInstanceInfo(appName, instanceId, hostname, ipAddr, port, preferIpAddress);
        }

        private InstanceInfo createInstanceInfo(String appName, String instanceId, String hostname, String ipAddr,
                                                int port,
                                                boolean preferIpAddress) {
            String appHostname = preferIpAddress ? ipAddr : hostname;
            return new InstanceInfo(appHostname, port, ipAddr, appName, instanceId);
        }

        /**
         * Selects eureka instance port.
         *
         * @param appCtx application context
         * @param server embedded server
         * @return eureka instance port
         * @see #PORT
         */
        private int selectEurekaPort(ApplicationContext appCtx, EmbeddedServer server) {
            return appCtx.get(PORT, String.class)
                    .flatMap(this::optString)
                    .map(Integer::parseInt)
                    .orElseGet(server::getPort);
        }

        /**
         * Selects eureka instance ip address.
         *
         * @param appCtx application context
         * @param server embedded server
         * @return eureka instance ip address
         * @see #IP_ADDRESS
         */
        private String selectEurekaIpAddress(ApplicationContext appCtx, EmbeddedServer server) {
            return appCtx.get(IP_ADDRESS, String.class)
                    .flatMap(this::optString)
                    .orElseGet(() -> lookupIp(server.getHost()));
        }

        /**
         * Selects eureka instance hostname.
         *
         * @param appCtx hostname defined in application configuration properties
         * @param server embedded server
         * @return eureka instance hostname
         * @see #HOSTNAME
         */
        private String selectEurekaHostname(ApplicationContext appCtx, EmbeddedServer server) {
            return appCtx.get(HOSTNAME, String.class)
                    .flatMap(this::optString)
                    .map(Optional::of)
                    .orElseGet(() -> optString(server.getHost()))
                    .orElse(SocketUtils.LOCALHOST);
        }

        /**
         * Selects eureka instance id.
         *
         * @param appCtx    application context
         * @param appConfig application config
         * @param appName   overridden app name
         * @return eureka instance id
         * @see #INSTANCE_ID
         */
        private String selectEurekaInstanceId(ApplicationContext appCtx,
                                              ApplicationConfiguration appConfig, String appName, String host, int port) {
            return appCtx.get(INSTANCE_ID, String.class)
                    .flatMap(this::optString)
                    .orElseGet(() -> defaultInstanceId(appConfig, appName, host, port));
        }

        /**
         * Creates eureka default instance id.
         *
         * @param appConfig application config
         * @param appName   eureka instance app name
         * @param host      eureka instance hostname
         * @param port      eureka instance port
         * @return default eureka instance id
         */
        private String defaultInstanceId(ApplicationConfiguration appConfig, String appName, String host, int port) {
            String finalAppName = optString(appName)
                    .map(Optional::of)
                    .orElseGet(() -> appConfig.getName().flatMap(this::optString))
                    .orElse(Environment.DEFAULT_NAME);
            return host + ":" + finalAppName + ":" + port;
        }

        /**
         * Chooses eureka service application name.
         *
         * @param appCtx    application context
         * @param appConfig application config
         * @return eureka service name
         * @see #APPNAME
         */
        private String selectApplicationName(ApplicationContext appCtx, ApplicationConfiguration appConfig) {
            return appCtx.get(APPNAME, String.class)
                    .flatMap(this::optString)
                    .map(Optional::of)
                    .orElseGet(appConfig::getName)
                    .orElse(Environment.DEFAULT_NAME);
        }

        private Optional<String> optString(String s) {
            return Optional.ofNullable(s)
                    .map(String::trim)
                    .filter(e -> !e.isEmpty());
        }

        /**
         * @return Is an instance ID explicitly specified
         */
        public boolean isExplicitInstanceId() {
            return true;
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
