/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpHeaders;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Represents a remote service discovered by the underlying discovery implementation.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ServiceInstance {

    /**
     * Constant to represent the group of the service contained with {@link #getMetadata()}.
     */
    String GROUP = "group";

    /**
     * Constant to represent the zone of the service contained with {@link #getMetadata()}.
     */
    String ZONE = "zone";

    /**
     * Constant to represent the region of the service contained with {@link #getMetadata()}.
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
     * Returns the availability zone to use. A zone is, for example, the AWS availability zone.
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
        return getMetadata().get(REGION, String.class);
    }

    /**
     * Returns the application group. For example, the AWS auto-scaling group.
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
     * Resolve a URI relative to this service instance.
     *
     * @param relativeURI The relative URI
     * @return The relative URI
     */
    default URI resolve(URI relativeURI) {
        URI thisUri = getURI();
        // if the URI features credentials strip this out
        if (StringUtils.isNotEmpty(thisUri.getUserInfo())) {
            try {
                thisUri = new URI(thisUri.getScheme(), null, thisUri.getHost(), thisUri.getPort(), thisUri.getPath(), thisUri.getQuery(), thisUri.getFragment());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("ServiceInstance URI is invalid: " + e.getMessage(), e);
            }
        }
        String rawQuery = thisUri.getRawQuery();
        if (StringUtils.isNotEmpty(rawQuery)) {
            return thisUri.resolve(relativeURI + "?" + rawQuery);
        } else {
            return thisUri.resolve(relativeURI);
        }
    }

    /**
     * Construct a new {@link ServiceInstance} for the given ID and URL.
     *
     * @param id  The ID
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
     * Construct a new {@link ServiceInstance} for the given ID and URL.
     *
     * @param id  The ID
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

            @Override
            public ConvertibleValues<String> getMetadata() {
                String userInfo = uri.getUserInfo();
                if (userInfo == null) {
                    return ServiceInstance.super.getMetadata();
                } else {
                    Map<String, String> metadata = new HashMap<>(1);
                    metadata.put(HttpHeaders.AUTHORIZATION_INFO, userInfo);
                    return ConvertibleValues.of(metadata);
                }
            }
        };
    }

    /**
     * Construct a new {@link ServiceInstance} for the given ID, host and port using the HTTP scheme.
     *
     * @param id   The ID
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

    /**
     * A builder to builder a {@link ServiceInstance}.
     *
     * @param id  The id
     * @param uri The URI
     * @return The builder
     */
    static Builder builder(String id, URI uri) {
        return new DefaultServiceInstance(id, uri);
    }

    /**
     * A builder for building {@link ServiceInstance} references.
     */
    interface Builder {
        /**
         * Sets the instance id.
         *
         * @param id The instance id
         * @return This builder
         */
        Builder instanceId(String id);

        /**
         * Sets the zone.
         *
         * @param zone The zone
         * @return This builder
         */
        Builder zone(String zone);

        /**
         * Sets the region.
         *
         * @param region The region
         * @return This builder
         */
        Builder region(String region);

        /**
         * Sets the application group.
         *
         * @param group The group
         * @return This builder
         */
        Builder group(String group);

        /**
         * Sets the application status.
         *
         * @param status The status
         * @return This builder
         */
        Builder status(HealthStatus status);

        /**
         * Sets the application metadata.
         *
         * @param metadata The metadata
         * @return This builder
         */
        Builder metadata(Map<String, String> metadata);

        /**
         * @return The instance
         */
        ServiceInstance build();
    }
}
