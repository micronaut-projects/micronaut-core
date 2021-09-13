/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.web.router.resource;

import java.net.URL;
import java.util.Optional;

/**
 * Interface for resolving static resources.
 *
 * @author graemerocher
 * @author James Kleeh
 * @since 1.0
 */
public interface StaticResourceResolver {
    /**
     * Empty resolver.
     */
    StaticResourceResolver EMPTY = resourcePath -> Optional.empty();

    /**
     * Resolves a path to a URL.
     *
     * @param resourcePath The path to the resource
     * @return The optional URL
     */
    Optional<URL> resolve(String resourcePath);
}
