/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.List;
import java.util.Optional;

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
     * Default constructor.
     *
     * @deprecated Use {@link #ResourceLoaderFactory(Environment)} instead.
     */
    @Deprecated
    public ResourceLoaderFactory() {
        this.classLoader = ResourceLoaderFactory.class.getClassLoader();
    }

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
    protected @NonNull ResourceResolver resourceResolver(@NonNull List<ResourceLoader> resourceLoaders) {
        return new ResourceResolver(resourceLoaders);
    }

    /**
     * Type converter for {@link Readable} types.
     * @param resourceResolver The resource resolver.
     * @return The type converter
     * @since 1.1.0
     */
    @Singleton
    protected @NonNull TypeConverter<CharSequence, Readable> readableTypeConverter(ResourceResolver resourceResolver) {
        return (object, targetType, context) -> {
            String pathStr = object.toString();
            Optional<ResourceLoader> supportingLoader = resourceResolver.getSupportingLoader(pathStr);
            if (!supportingLoader.isPresent()) {
                context.reject(pathStr, new ConfigurationException(
                        "No supported resource loader for path [" + pathStr + "]. Prefix the path with a supported prefix such as 'classpath:' or 'file:'"
                ));
                return Optional.empty();
            } else {
                final Optional<URL> resource = resourceResolver.getResource(pathStr);
                if (resource.isPresent()) {
                    return Optional.of(Readable.of(resource.get()));
                } else {
                    context.reject(object, new ConfigurationException("No resource exists for value: " + object));
                    return Optional.empty();
                }
            }

        };
    }
}
