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
package io.micronaut.core.io;

import io.micronaut.core.annotation.Indexed;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Basic abstraction over resource loading.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(ResourceLoader.class)
public interface ResourceLoader {

    /**
     * Obtains a resource as a stream.
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<InputStream> getResourceAsStream(String path);

    /**
     * Obtains the URL to a given resource.
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<URL> getResource(String path);

    /**
     * Obtains all resources with the given name.
     *
     * @param name The name of the resource
     * @return A stream of URLs
     */
    Stream<URL> getResources(String name);

    /**
     * @param path The path to a resource including a prefix
     *             appended by a colon. Ex (classpath:, file:)
     * @return Whether the given resource loader supports the prefix
     */
    boolean supportsPrefix(String path);

    /**
     * Constructs a new resource loader designed to load
     * resources from the given path. Requested resources
     * will be loaded within the context of the given path.
     *
     * @param basePath The path to load resources
     * @return The new {@link ResourceLoader}
     */
    ResourceLoader forBase(String basePath);

}
