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
package io.micronaut.web.router.resource;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves resources from a set of resource loaders.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class StaticResourceResolver {
    /**
     * An empty resolver to use as a constant.
     */
    public static final StaticResourceResolver EMPTY = new StaticResourceResolver(Collections.emptyList()) {
        @Override
        public Optional<URL> resolve(String resourcePath) {
            return Optional.empty();
        }
    };

    private static final String INDEX_PAGE = "index.html";
    private final @Nullable AntPathMatcher pathMatcher;
    private final Map<String, List<ResourceLoader>> resourceMappings;

    /**
     * Default constructor.
     *
     * @param configurations The static resource configurations
     */
    StaticResourceResolver(List<StaticResourceConfiguration> configurations) {
        if (CollectionUtils.isEmpty(configurations)) {
            this.pathMatcher = null;
            this.resourceMappings = Collections.emptyMap();
        } else {
            this.resourceMappings = new LinkedHashMap<>();
            this.pathMatcher = PathMatcher.ANT;
            if (CollectionUtils.isNotEmpty(configurations)) {
                for (StaticResourceConfiguration config: configurations) {
                    if (config.isEnabled()) {
                        this.resourceMappings.put(config.getMapping(), config.getResourceLoaders());
                    }
                }
            }
        }
    }

    /**
     * Resolves a path to a URL.
     *
     * @param resourcePath The path to the resource
     * @return The optional URL
     */
    public Optional<URL> resolve(String resourcePath) {
        for (Map.Entry<String, List<ResourceLoader>> entry : resourceMappings.entrySet()) {
            List<ResourceLoader> loaders = entry.getValue();
            String mapping = entry.getKey();
            if (!loaders.isEmpty() && pathMatcher != null && pathMatcher.matches(mapping, resourcePath)) {
                String path = pathMatcher.extractPathWithinPattern(mapping, resourcePath);
                Optional<URL> resource = resolve(loaders, path, INDEX_PAGE);
                if (resource.isPresent()) {
                    return resource;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves the path of a resource, relative to the base of the resource loaders, to a URL: the
     * first loader that has the resource provides it. An empty path is the index page, and a path
     * without an extension that no loader has, e.g. a directory, is tried with the index page
     * under it.
     *
     * @param loaders   The resource loaders, tried in order
     * @param path      The path of the resource, relative to the base of the loaders
     * @param indexPage The name of the index page, or {@code null} for none
     * @return The URL of the resource, if a loader has it
     * @since 5.3.0
     */
    @Internal
    public static Optional<URL> resolve(List<ResourceLoader> loaders, String path, @Nullable String indexPage) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        boolean root = StringUtils.isEmpty(path);
        if (root) {
            //A request to the root of the mapping
            if (indexPage == null) {
                return Optional.empty();
            }
            path = indexPage;
        }
        String indexPath = null;
        if (!root && indexPage != null && path.indexOf('.') == -1) {
            indexPath = (path.endsWith("/") ? path : path + "/") + indexPage;
        }
        for (ResourceLoader loader : loaders) {
            Optional<URL> resource = loader.getResource(path);
            if (resource.isPresent()) {
                return resource;
            }
            if (indexPath != null) {
                resource = loader.getResource(indexPath);
                if (resource.isPresent()) {
                    return resource;
                }
            }
        }
        return Optional.empty();
    }
}
