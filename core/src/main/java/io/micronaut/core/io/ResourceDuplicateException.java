/*
 * Copyright 2017-2026 original authors
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

import org.jspecify.annotations.NullMarked;

import java.net.URL;
import java.util.List;

/**
 * Exception thrown when duplicate resources are detected.
 *
 * @since 5.0.0
 */
@NullMarked
public final class ResourceDuplicateException extends RuntimeException {
    private final String resourceName;
    private final List<URL> resources;

    /**
     * @param resourceName The resource name
     * @param resources    The resolved resources
     * @since 5.0.0
     */
    public ResourceDuplicateException(String resourceName, List<URL> resources) {
        super("Duplicate resource detected: " + resourceName);
        this.resourceName = resourceName;
        this.resources = List.copyOf(resources);
    }

    /**
     * Returns the resource name.
     *
     * @return The resource name
     * @since 5.0.0
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Returns the resolved resources.
     *
     * @return The resolved resources
     * @since 5.0.0
     */
    public List<URL> getResources() {
        return resources;
    }
}
