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
package org.particleframework.discovery;

import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.health.HealthStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

/**
 * <p>Represents a remote service discovered by the underlying discovery implementation</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ServiceInstance {
    /**
     * Constant to represent the group of the service contained with {@link #getMetadata()}
     */
    String GROUP = "group";

    /**
     * Constant to represent the zone of the service contained with {@link #getMetadata()}
     */
    String ZONE = "zone";

    /**
     * Constant to represent the region of the service contained with {@link #getMetadata()}
     */
    String REGION = "region";
    /**
     * @return The identifier of the service used for purposes of service discovery
     */
    String getId();

    /**
     * @return The service URI
     */
    URI getURI();

    /**
     * @return The {@link HealthStatus} of the instance
     */
    default HealthStatus getHealthStatus() {
        return HealthStatus.UP;
    }
    /**
     * @return The ID of the instance
     */
    default Optional<String> getInstanceId() {
        return Optional.empty();
    }

    /**
     * Returns the availability zone to use. A zone is, for example, the AWS availability zone
     *
     * @return The zone to use
     */
    default Optional<String> getZone() {
        return getMetadata().get(ZONE, String.class);
    }

    /**
     * Returns the region to use. A region is, for example, the AWS region
     *
     * @return The region
     */
    default Optional<String> getRegion() {
        return getMetadata().get(ZONE, String.class);
    }
    /**
     * Returns the application group. For example, the AWS auto-scaling group
     *
     * @return The group to use
     */
    default Optional<String> getGroup() {
        return getMetadata().get(GROUP, String.class);
    }
    /**
     * @return The service metadata
     */
    default ConvertibleValues<String> getMetadata() {
        return ConvertibleValues.empty();
    }

    /**
     * @return The service host
     */
    default String getHost() {
        return getURI().getHost();
    }

    /**
     * @return Is the service instance available over a secure connection
     */
    default boolean isSecure() {
        String scheme = getURI().getScheme();
        return scheme != null && scheme.equalsIgnoreCase("https");
    }

    /**
     * @return The service port
     */
    default int getPort() {
        return getURI().getPort();
    }

    /**
     * Resolve a URI relative to this service instance
     * @param relativeURI The relative URI
     * @return The relative URI
     */
    default URI resolve(URI relativeURI) {
        return getURI().resolve(relativeURI);
    }

    /**
     * Construct a new {@link ServiceInstance} for the given ID and URL
     * @param id The ID
     * @param url The URL
     * @return The instance
     */
    static ServiceInstance of(String id, URL url) {
        try {
            URI uri = url.toURI();
            return of(id, uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI argument: " + url);
        }
    }

    /**
     * Construct a new {@link ServiceInstance} for the given ID and URL
     * @param id The ID
     * @param uri The URI
     * @return The instance
     */
    static ServiceInstance of(String id, URI uri) {
        return new ServiceInstance() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public URI getURI() {
                return uri;
            }
        };
    }

    /**
     * Construct a new {@link ServiceInstance} for the given ID, host and port using the HTTP scheme
     * @param id The ID
     * @param host The host
     * @param port The port
     * @return The instance
     */
    static ServiceInstance of(String id, String host, int port) {
        return new ServiceInstance() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public URI getURI() {
                return URI.create("http://" + host + ":" + port);
            }
        };
    }
}
