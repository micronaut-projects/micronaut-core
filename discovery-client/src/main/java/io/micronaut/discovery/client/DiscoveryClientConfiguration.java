/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.client;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract class for all {@link io.micronaut.discovery.DiscoveryClient} configurations.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class DiscoveryClientConfiguration extends HttpClientConfiguration {

    private final ApplicationConfiguration applicationConfiguration;
    private List<ServiceInstance> defaultZone = Collections.emptyList();
    private List<ServiceInstance> otherZones = Collections.emptyList();

    private String host = SocketUtils.LOCALHOST;
    private int port = -1;
    private boolean secure;
    private boolean shouldUseDns = false;
    private String contextPath;

    /**
     * Default constructor.
     */
    public DiscoveryClientConfiguration() {
        this.applicationConfiguration = null;
    }

    /**
     * @param applicationConfiguration The application configuration.
     */
    public DiscoveryClientConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * Whether DNS should be used to resolve the discovery servers.
     *
     * @return True if DNS should be used.
     */
    @Experimental
    public boolean isShouldUseDns() {
        return shouldUseDns;
    }

    /**
     * Whether DNS should be used to resolve the discovery servers.
     *
     * @param shouldUseDns True if DNS should be used.
     */
    @Experimental
    public void setShouldUseDns(boolean shouldUseDns) {
        this.shouldUseDns = shouldUseDns;
    }

    /**
     * @return The context path to use
     */
    public Optional<String> getContextPath() {
        return Optional.ofNullable(contextPath);
    }

    /**
     * Sets the context path.
     * @param contextPath The context path
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * @return Resolves the service ID to use
     */
    public Optional<String> getServiceId() {
        if (applicationConfiguration != null) {
            return applicationConfiguration.getName();
        }
        return Optional.empty();
    }

    /**
     * @return The Discovery servers within the default zone
     */
    public List<ServiceInstance> getDefaultZone() {
        return defaultZone;
    }

    /**
     * Sets the Discovery servers to use for the default zone.
     *
     * @param defaultZone The default zone
     */
    public void setDefaultZone(List<URL> defaultZone) {
        this.defaultZone = defaultZone
            .stream()
            .map(uriMapper())
            .map(uri -> ServiceInstance.builder(getServiceID(), uri).build())
            .collect(Collectors.toList());
    }

    /**
     * @return The Discovery servers within all zones
     */
    public List<ServiceInstance> getAllZones() {
        List<ServiceInstance> allZones = new ArrayList<>(defaultZone.size() + otherZones.size());
        allZones.addAll(defaultZone);
        allZones.addAll(otherZones);
        return allZones;
    }

    /**
     * Configures Discovery servers in other zones.
     *
     * @param zones The zones
     */
    public void setZones(Map<String, List<URL>> zones) {
        if (zones != null) {
            this.otherZones = zones.entrySet()
                .stream()
                .flatMap((Function<Map.Entry<String, List<URL>>, Stream<ServiceInstance>>) entry ->
                    entry.getValue()
                        .stream()
                        .map(uriMapper())
                        .map(uri ->
                            ServiceInstance.builder(getServiceID(), uri)
                                .zone(entry.getKey())
                                .build()
                        ))
                .collect(Collectors.toList());
        }
    }

    /**
     * @return Is the discovery server exposed over HTTPS (defaults to false)
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * @param secure Set if the discovery server is exposed over HTTPS
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /**
     * @return The Discovery server instance host name. Defaults to 'localhost'.
     **/
    @NonNull
    public String getHost() {
        return host;
    }

    /**
     * @param host The Discovery server host name
     */
    public void setHost(String host) {
        if (StringUtils.isNotEmpty(host)) {
            this.host = host;
        }
    }

    /**
     * @return The default Discovery server port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port The port for the Discovery server
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return The default discovery configuration
     */
    @NonNull
    public abstract DiscoveryConfiguration getDiscovery();

    /**
     * @return The default registration configuration
     */
    @Nullable
    public abstract RegistrationConfiguration getRegistration();

    @Override
    public String toString() {
        return "DiscoveryClientConfiguration{" +
            "defaultZone=" + defaultZone +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", secure=" + secure +
            "} ";
    }

    /**
     * @return The ID of the {@link io.micronaut.discovery.DiscoveryClient}
     */
    protected abstract String getServiceID();

    private Function<URL, URI> uriMapper() {
        return url -> {
            try {
                return url.toURI();
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Invalid Eureka server URL: " + url);
            }
        };
    }
}
