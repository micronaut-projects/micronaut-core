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
package io.micronaut.web.router.resource;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.context.ServerContextPathProvider;

import javax.annotation.Nullable;
import javax.inject.Inject;
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
     * The prefix for static resources configuration.
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

    private boolean enabled = DEFAULT_ENABLED;
    private List<String> paths = Collections.emptyList();
    private String mapping = DEFAULT_MAPPING;

    private final ResourceResolver resourceResolver;
    private final ServerContextPathProvider contextPathProvider;

    /**
     * @param resourceResolver The {@linkplain ResourceResolver}
     * @deprecated Use {@link #StaticResourceConfiguration(ResourceResolver, ServerContextPathProvider)} instead.
     */
    @Deprecated
    public StaticResourceConfiguration(ResourceResolver resourceResolver) {
        this(resourceResolver, null);
    }

    /**
     * @param resourceResolver The {@linkplain ResourceResolver}
     * @param contextPathProvider The context path provider
     */
    @Inject
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
                if (path.equals("classpath:")) {
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
