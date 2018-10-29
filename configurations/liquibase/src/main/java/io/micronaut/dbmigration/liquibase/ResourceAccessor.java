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

package io.micronaut.dbmigration.liquibase;

import io.micronaut.core.io.ResourceResolver;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Micronaut bean implementing {@link liquibase.resource.ResourceAccessor}.
 *
 * @author Sergio del Amo
 * @since 1.1
 */
@Singleton
public class ResourceAccessor implements liquibase.resource.ResourceAccessor {

    private final ResourceResolver resourceResolver;

    /**
     * @param resourceResolver The {@linkplain ResourceResolver}
     */
    public ResourceAccessor(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Set<InputStream> getResourcesAsStream(String path) throws IOException {
        Set<InputStream> result = new HashSet<>();
        Optional<InputStream> inputStreamOptional = resourceResolver.getResourceAsStream(path);
        inputStreamOptional.ifPresent(result::add);
        return result;
    }

    @Override
    public Set<String> list(String relativeTo, String path, boolean includeFiles, boolean includeDirectories, boolean recursive) throws IOException {
        throw new IllegalArgumentException("liquibase.resource.ResourceAccessor:list not implemented");
    }

    @Override
    public ClassLoader toClassLoader() {
        throw new IllegalArgumentException("liquibase.resource.ResourceAccessor:toClassLoader not implemented");
    }
}
