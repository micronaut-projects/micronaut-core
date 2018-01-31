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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

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
     * @return The identifier of the service
     */
    String getId();

    /**
     * @return The service URI
     */
    URI getURI();

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
