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

package io.micronaut.configuration.dbmigration.liquibase;

import io.micronaut.context.env.Environment;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Micronaut bean implementing {@link liquibase.resource.ResourceAccessor}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class LiquibaseResourceAccessor extends CompositeResourceAccessor {

    /**
     * @param environment The Micronaut environment
     */
    public LiquibaseResourceAccessor(Environment environment) {
        super(buildResourceAccessors(environment));
    }

    @Override
    public Set<InputStream> getResourcesAsStream(String path) throws IOException {
        return super.getResourcesAsStream(normalize(path));
    }

    @Override
    public Set<String> list(String relativeTo, String path, boolean includeFiles, boolean includeDirectories, boolean recursive) throws IOException {
        return super.list(normalize(relativeTo), path, includeFiles, includeDirectories, recursive);
    }

    private String normalize(String path) {
        if (path != null) {
            if (path.startsWith("classpath:")) {
                path = path.substring(10);
            }
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
        }
        return path;
    }

    protected static List<ResourceAccessor> buildResourceAccessors(Environment environment) {
        List<ResourceAccessor> resourceAccessors = new ArrayList<>(2);
        resourceAccessors.add(new ClassLoaderResourceAccessor(environment.getClassLoader()));
        resourceAccessors.add(new FileSystemResourceAccessor());
        return resourceAccessors;
    }
}
