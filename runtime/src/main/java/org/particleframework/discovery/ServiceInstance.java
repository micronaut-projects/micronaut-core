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

/**
 * <p>Represents a remote service discovered by the underlying discovery implementation</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ServiceInstance {

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
}
