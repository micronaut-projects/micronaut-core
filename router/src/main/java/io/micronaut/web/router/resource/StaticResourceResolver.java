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
import java.util.function.Predicate;

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
    private final AntPathMatcher pathMatcher = PathMatcher.ANT;
    private volatile Map<String, List<ResourceLoader>> resourceMappings; // NOSONAR java:S3077 a fresh map per update, never mutated once published

    /**
     * Default constructor.
     *
     * @param configurations The static resource configurations
     */
    StaticResourceResolver(List<StaticResourceConfiguration> configurations) {
        this.resourceMappings = mappingsOf(configurations);
    }

    /**
     * Replaces the mappings with the ones of the given configurations, which the factory does when
     * the {@code micronaut.router.static-resources} configuration changed.
     *
     * @param configurations The static resource configurations
     * @since 5.3.0
     */
    @Internal
    public void update(List<StaticResourceConfiguration> configurations) {
        this.resourceMappings = mappingsOf(configurations);
    }

    private static Map<String, List<ResourceLoader>> mappingsOf(List<StaticResourceConfiguration> configurations) {
        if (CollectionUtils.isEmpty(configurations)) {
            return Collections.emptyMap();
        }
        Map<String, List<ResourceLoader>> mappings = new LinkedHashMap<>();
        for (StaticResourceConfiguration config : configurations) {
            if (config.isEnabled()) {
                mappings.put(config.getMapping(), config.getResourceLoaders());
            }
        }
        return mappings;
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
            if (!loaders.isEmpty() && pathMatcher.matches(mapping, resourcePath)) {
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
        return resolveResource(loaders, path, indexPage).map(ResolvedResource::url);
    }

    /**
     * Resolves the path of a resource like {@link #resolve(List, String, String)}, and tells
     * whether the URL is the index page of the path.
     *
     * @param loaders   The resource loaders, tried in order
     * @param path      The path of the resource, relative to the base of the loaders
     * @param indexPage The name of the index page, or {@code null} for none
     * @return The resource, if a loader has it
     * @since 5.3.0
     */
    @Internal
    public static Optional<ResolvedResource> resolveResource(List<ResourceLoader> loaders, String path, @Nullable String indexPage) {
        return resolveResource(loaders, path, indexPage, resource -> true);
    }

    /**
     * Resolves the path of a resource like {@link #resolveResource(List, String, String)}, taking
     * only the resources a predicate accepts: a resource it refuses, e.g. another spelling of the
     * path on a file system that ignores the case, is a miss, and the next location is tried.
     *
     * @param loaders    The resource loaders, tried in order
     * @param path       The path of the resource, relative to the base of the loaders
     * @param indexPage  The name of the index page, or {@code null} for none
     * @param acceptable Whether a resource of a loader is the resource
     * @return The resource, if a loader has one the predicate accepts
     * @since 5.3.0
     */
    @Internal
    public static Optional<ResolvedResource> resolveResource(List<ResourceLoader> loaders, String path, @Nullable String indexPage,
                                                             Predicate<? super ResolvedResource> acceptable) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        boolean root = StringUtils.isEmpty(path);
        if (root) {
            //A request to the root of the mapping
            if (indexPage == null) {
                return Optional.empty();
            }
            for (ResourceLoader loader : loaders) {
                Optional<ResolvedResource> resource = accepted(loader, indexPage, true, acceptable);
                if (resource.isPresent()) {
                    return resource;
                }
            }
            return Optional.empty();
        }
        String indexPath = null;
        if (!root && indexPage != null && path.indexOf('.', path.lastIndexOf('/') + 1) == -1) {
            // a path whose last segment has no extension may be a directory
            indexPath = (path.endsWith("/") ? path : path + "/") + indexPage;
        }
        for (ResourceLoader loader : loaders) {
            Optional<ResolvedResource> resource = accepted(loader, path, false, acceptable);
            if (resource.isPresent()) {
                return resource;
            }
            if (indexPath != null) {
                resource = accepted(loader, indexPath, true, acceptable);
                if (resource.isPresent()) {
                    return resource;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ResolvedResource> accepted(ResourceLoader loader, String path, boolean index,
                                                       Predicate<? super ResolvedResource> acceptable) {
        return loader.getResource(path).map(url -> new ResolvedResource(url, index)).filter(acceptable);
    }

    /**
     * A resolved resource.
     *
     * @param url   The URL of the resource
     * @param index Whether it is the index page of the path, not the path itself
     * @since 5.3.0
     */
    @Internal
    public record ResolvedResource(URL url, boolean index) {
    }
}
