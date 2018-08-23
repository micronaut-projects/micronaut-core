/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.Toggleable;

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
@ConfigurationProperties(StaticResourceConfiguration.PREFIX)
public class StaticResourceConfiguration implements Toggleable {

    /**
     * The prefix for static resources configuration.
     */
    public static final String PREFIX = "micronaut.router.static.resources";

    protected boolean enabled = false;
    protected List<String> paths = Collections.emptyList();
    protected String mapping = "/**";

    private final ResourceResolver resourceResolver;

    /**
     * @param resourceResolver The {@linkplain ResourceResolver}
     */
    public StaticResourceConfiguration(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
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
}
