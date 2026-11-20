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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.context.ServerContextPathProvider;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Stores configuration for the loading of static resources.
 *
 * @author James Kleeh
 * @since 1.0
 */
@EachProperty(StaticResourceConfiguration.PREFIX)
public class StaticResourceConfiguration implements Toggleable {

    /**
     * The prefix for static resource configuration.
     */
    public static final String PREFIX = "micronaut.router.static-resources";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default mapping value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_MAPPING = "/**";

    /**
     * The default value of serving pre-compressed files.
     *
     * @since 5.3.0
     */
    public static final boolean DEFAULT_PRE_COMPRESSED = false;

    /**
     * The default number of resources cached in memory: none.
     *
     * @since 5.3.0
     */
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 0;

    /**
     * The default size of the largest resource cached in memory, in bytes.
     *
     * @since 5.3.0
     */
    public static final int DEFAULT_CACHE_MAX_ENTRY_SIZE = 65536;

    private boolean enabled = DEFAULT_ENABLED;
    private List<String> paths = Collections.emptyList();
    private String mapping = DEFAULT_MAPPING;
    private boolean preCompressed = DEFAULT_PRE_COMPRESSED;
    private int cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;
    private int cacheMaxEntrySize = DEFAULT_CACHE_MAX_ENTRY_SIZE;

    private final ResourceResolver resourceResolver;
    private final @Nullable ServerContextPathProvider contextPathProvider;

    /**
     * @param resourceResolver The {@linkplain ResourceResolver}
     * @param contextPathProvider The context path provider
     */
    public StaticResourceConfiguration(ResourceResolver resourceResolver,
                                       @Nullable ServerContextPathProvider contextPathProvider) {
        this.resourceResolver = resourceResolver;
        this.contextPathProvider = contextPathProvider;
    }

    /**
     * @return Enable the static resources router
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The list of {@link ResourceLoader} available for the path
     */
    public List<ResourceLoader> getResourceLoaders() {
        if (enabled) {
            List<ResourceLoader> loaders = new ArrayList<>(paths.size());
            for (String path : paths) {
                if (path.equals(ClassPathResourceLoader.PREFIX)) {
                    throw new ConfigurationException("A path value of [classpath:] will allow access to class files!");
                }
                Optional<ResourceLoader> loader = resourceResolver.getLoaderForBasePath(path);
                if (loader.isPresent()) {
                    loaders.add(loader.get());
                } else {
                    throw new ConfigurationException("Unrecognizable resource path: " + path);
                }
            }
            return loaders;
        }
        return Collections.emptyList();
    }

    /**
     * The static resource mapping.
     * @return The mapping
     */
    public String getMapping() {
        return mapping;
    }

    /**
     * @return Whether the pre-compressed siblings of the resources are served
     * @since 5.3.0
     */
    public boolean isPreCompressed() {
        return preCompressed;
    }

    /**
     * Whether to serve the pre-compressed sibling of a resource to a request that accepts its encoding: for `app.js`,
     * the file `app.js.br` (`br`), else `app.js.zst` (`zstd`), else `app.js.gz` (`gzip`), following the weights of the
     * `Accept-Encoding` header. The response has the `Content-Type` of the resource, the `Content-Encoding` of the
     * sibling, `Vary: Accept-Encoding` and an `ETag` of its own. A request with a `Range` header is served the
     * resource itself. Default value ({@value #DEFAULT_PRE_COMPRESSED}).
     *
     * @param preCompressed True to serve the pre-compressed siblings
     * @since 5.3.0
     */
    public void setPreCompressed(boolean preCompressed) {
        this.preCompressed = preCompressed;
    }

    /**
     * @return The most resources of the mapping cached in memory, {@code 0} for none
     * @since 5.3.0
     */
    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    /**
     * The most resources of the mapping whose content is cached in memory, and served without reading the file. A file
     * of the file system is loaded again when its last-modified time or length changes. A request with a `Range` header
     * is served from the file. Default value ({@value #DEFAULT_CACHE_MAX_ENTRIES}): no cache.
     *
     * @param cacheMaxEntries The most entries, {@code 0} for no cache
     * @since 5.3.0
     */
    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = Math.max(0, cacheMaxEntries);
    }

    /**
     * @return The size of the largest resource cached in memory, in bytes
     * @since 5.3.0
     */
    public int getCacheMaxEntrySize() {
        return cacheMaxEntrySize;
    }

    /**
     * The size of the largest resource cached in memory, in bytes; a larger one is served from the file. Default value
     * ({@value #DEFAULT_CACHE_MAX_ENTRY_SIZE}).
     *
     * @param cacheMaxEntrySize The size in bytes
     * @since 5.3.0
     */
    public void setCacheMaxEntrySize(int cacheMaxEntrySize) {
        if (cacheMaxEntrySize > 0) {
            this.cacheMaxEntrySize = cacheMaxEntrySize;
        }
    }

    /**
     * Sets whether this specific mapping is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if they are enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * A list of paths either starting with `classpath:` or `file:`. You can serve files from anywhere on disk or the classpath. For example to serve static resources from `src/main/resources/public`, you would use `classpath:public` as the path.
     *
     * @param paths The paths
     */
    public void setPaths(List<String> paths) {
        if (CollectionUtils.isNotEmpty(paths)) {
            this.paths = paths;
        }
    }

    /**
     * The path resources should be served from. Uses ant path matching. Default value ({@value #DEFAULT_MAPPING}).
     *
     * @param mapping The mapping
     */
    public void setMapping(String mapping) {
        if (StringUtils.isNotEmpty(mapping)) {
            String contextPath = contextPathProvider != null ? contextPathProvider.getContextPath() : null;
            if (contextPath != null && !mapping.startsWith(contextPath)) {
                this.mapping = StringUtils.prependUri(contextPath, mapping);
            } else {
                this.mapping = mapping;
            }
        }
    }
}
