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
package io.micronaut.http.resource;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Creates beans for {@link ResourceLoader}s to handle static resource requests. Registers a resource resolver that
 * uses those beans.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
@Factory
@BootstrapContextCompatible
public class ResourceLoaderFactory {

    private final ClassLoader classLoader;

    /**
     * The resource factory.
     *
     * @param environment The environment
     */
    @Inject
    public ResourceLoaderFactory(Environment environment) {
        this.classLoader = environment.getClassLoader();
    }

    /**
     * @return The class path resource loader
     */
    @Singleton
    @BootstrapContextCompatible
    protected @NonNull ClassPathResourceLoader getClassPathResourceLoader() {
        return new DefaultClassPathResourceLoader(classLoader);
    }

    /**
     * @return The file system resource loader
     */
    @Singleton
    @BootstrapContextCompatible
    protected @NonNull FileSystemResourceLoader fileSystemResourceLoader() {
        return new DefaultFileSystemResourceLoader();
    }

    /**
     * @param resourceLoaders The resource loaders
     * @return The resource resolver
     */
    @Singleton
    @BootstrapContextCompatible
    @Indexed(ResourceResolver.class)
    protected @NonNull ResourceResolver resourceResolver(@NonNull List<ResourceLoader> resourceLoaders) {
        return new ResourceResolver(resourceLoaders);
    }
}
